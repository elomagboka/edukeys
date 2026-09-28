#!/usr/bin/env bash
# Vérification par mutation du test d'isolation multi-établissement pour UNE
# entité filtrée (IsolationEtablissementTest, cas C1 à C10).
#
# Usage, depuis Git Bash, à la racine du dépôt, Docker démarré :
#     bash scripts/mutation-isolation.sh DecisionAdmission
#
# Voir scripts/README.md : quand le lancer, comment lire la matrice.
set -euo pipefail

ENTITE=${1:?"Usage : bash scripts/mutation-isolation.sh <NomEntite>  (ex. DecisionAdmission)"}
FABRIQUE="Fabrique${ENTITE}"

RACINE=$(git rev-parse --show-toplevel)
BACKEND="$RACINE/backend"
T=src/test/java/tg/novadigital/edukeys
M=src/main/java/tg/novadigital/edukeys
REGISTRE=$T/testsupport/FabriquesEntitesTest.java
TEST=$T/common/multietablissement/IsolationEtablissementTest.java
ARMEUR=$M/common/multietablissement/ArmeurFiltreEtablissement.java
GARDE=$M/common/multietablissement/GardeContexteEtablissement.java
REMPLISSEUR=$M/common/domain/RemplisseurEtablissement.java
FICHIERS=("$REGISTRE" "$TEST" "$ARMEUR" "$GARDE" "$REMPLISSEUR")
RAPPORT=target/surefire-reports/TEST-tg.novadigital.edukeys.common.multietablissement.IsolationEtablissementTest.xml
JOURNAUX="$BACKEND/target/mutation-isolation/$ENTITE"
CAS=(c1 c2 c3 c4 c5 c6 c7 c8 c9 c10)

cd "$BACKEND"

# --- Préconditions ----------------------------------------------------------
if ! grep -q "new ${FABRIQUE}()" "$REGISTRE"; then
    echo "Aucune ${FABRIQUE} enregistrée dans FabriquesEntitesTest : enregistre-la d'abord (le test D2 l'exige)." >&2
    exit 2
fi
if ! git diff --quiet -- "${FICHIERS[@]}" || ! git diff --cached --quiet -- "${FICHIERS[@]}"; then
    echo "Modifications locales dans les fichiers mutés : commite-les ou mets-les de côté avant de lancer." >&2
    git status --short -- "${FICHIERS[@]}" >&2
    exit 2
fi
if ! docker info >/dev/null 2>&1; then
    echo "Docker est arrêté : Testcontainers en a besoin." >&2
    exit 2
fi

restaurer() { git checkout -q -- "${FICHIERS[@]}"; }
trap restaurer EXIT INT TERM
mkdir -p "$JOURNAUX"

# --- Mutations --------------------------------------------------------------
# Chaque mutation est repérée par un motif, jamais par un numéro de ligne, et
# ÉCHOUE si le motif est introuvable : une mutation qui ne s'applique pas donne
# une ligne toute verte qui ressemble à un défaut de détection.
muter() { # muter <fichier> <expression perl de substitution>
    local fichier=$1 expr=$2
    if ! perl -0pi -e "\$n += ($expr); END { exit(\$n ? 0 : 3) }" "$fichier"; then
        echo "Mutation introuvable dans $fichier : $expr" >&2
        echo "Le code gardé a changé : mets à jour le motif dans scripts/mutation-isolation.sh." >&2
        exit 3
    fi
}

appliquer() {
    case $1 in
        seul)        muter "$REGISTRE"    "s{^(\s*)(FABRIQUES\.add\(new (?!${FABRIQUE}\(\))\w+\(\)\);)}{\$1//MUT \$2}mg" ;;
        filtre)      muter "$ARMEUR"      's{^(\s*)(session\.enableFilter\(NOM_FILTRE\)\.setParameter\(PARAMETRE_FILTRE, etablissementId\);)}{$1//MUT $2}m' ;;
        sansC0)      muter "$TEST"        's{\@BeforeEach(\s*\n\s*void c0_)}{//MUT$1}' ;;
        garde)       muter "$GARDE"       's#if \(ContexteEtablissement\.courant\(\)\.isEmpty\(\)\) \{#if (false) { //MUT#' ;;
        preupdate)   muter "$REMPLISSEUR" 's{(public void verifierAvantMiseAJour\(.*?)(refuserSiInterEtablissement\()}{$1//MUT $2}s' ;;
        prepersist)  muter "$REMPLISSEUR" 's{throw new ContexteEtablissementAbsentException\(\);}{//MUT}'
                     muter "$REMPLISSEUR" 's{(public void remplir\(.*?)(refuserSiInterEtablissement\()}{$1//MUT $2}s' ;;
        remplissage) muter "$REMPLISSEUR" "s{(entite\.setEtablissementId\(etablissementCourant\);)}{if (!nomEntite.equals(\"${ENTITE}\")) \$1 //MUT}" ;;
        *) echo "Mutation inconnue : $1" >&2; exit 3 ;;
    esac
}

# --- Matrice : nom | mutations | cas attendus rouges | description -----------
LIGNES=(
    "temoin|seul||aucune mutation"
    "filtre|seul filtre|c1 c2 c3 c4 c5 c6 c7 c8 c9 c10|filtre desarme, C0 actif"
    "filtreSansC0|seul filtre sansC0|c1 c2 c3 c4|filtre desarme, C0 neutralise"
    "preupdate|seul preupdate|c8|@PreUpdate desarme"
    "prepersist|seul prepersist|c5 c10|refus @PrePersist desarmes"
    "garde|seul garde|c6|garde AOP desarmee"
    "remplissage|seul remplissage|c1 c2 c3 c4 c6 c8 c9|remplissage coupe pour ${ENTITE}"
)

etat() { # etat <cas> -> R | . | ?
    local bloc
    bloc=$(awk -v c="$1" '/<testcase /{p=0} $0 ~ "<testcase name=\""c"_"{p=1} p' "$RAPPORT" 2>/dev/null || true)
    if [ -z "$bloc" ]; then echo "?"; elif grep -q '<failure\|<error' <<<"$bloc"; then echo "R"; else echo "."; fi
}

ECARTS=0
AVERTISSEMENTS=0
MATRICE=$(printf "%-14s" "mutation"; for c in "${CAS[@]}"; do printf "%-5s" "${c^^}"; done)

for ligne in "${LIGNES[@]}"; do
    IFS='|' read -r nom mutations attendus description <<<"$ligne"
    restaurer
    for m in $mutations; do appliquer "$m"; done
    echo "== $nom ($description) ..."
    rm -f "$RAPPORT"
    mvn -q test -Dtest='IsolationEtablissementTest#c*' >"$JOURNAUX/$nom.log" 2>&1 || true
    cp "$RAPPORT" "$JOURNAUX/$nom.xml" 2>/dev/null || true

    MATRICE+=$'\n'$(printf "%-14s" "$nom")
    for c in "${CAS[@]}"; do
        e=$(etat "$c")
        attendu_rouge=0
        [[ " $attendus " == *" $c "* ]] && attendu_rouge=1
        marque=$e
        if [ "$e" = "?" ]; then
            marque="?!"; ECARTS=$((ECARTS + 1))
        elif [ "$attendu_rouge" = 1 ] && [ "$e" = "." ]; then
            marque=".!"; ECARTS=$((ECARTS + 1))       # défaut non détecté : bloquant
        elif [ "$attendu_rouge" = 0 ] && [ "$e" = "R" ]; then
            marque="R?"; AVERTISSEMENTS=$((AVERTISSEMENTS + 1))
        fi
        MATRICE+=$(printf "%-5s" "$marque")
    done
done

restaurer
trap - EXIT INT TERM

echo
echo "Matrice de mutation — ${ENTITE}   (R rouge, . vert ; .! non detecte, R? rouge inattendu, ?! cas absent)"
echo "$MATRICE"
echo
echo "Journaux et rapports : $JOURNAUX"
if [ "$ECARTS" -gt 0 ]; then
    echo "ECHEC : $ECARTS ecart(s) bloquant(s) — un defaut injecte n'a pas ete detecte pour ${ENTITE}." >&2
    exit 1
fi
if [ "$AVERTISSEMENTS" -gt 0 ]; then
    echo "OK avec $AVERTISSEMENTS rouge(s) inattendu(s) : lire le journal correspondant (echec de preparation ?)."
else
    echo "OK : chaque garde injectee est detectee pour ${ENTITE}, conforme a la matrice attendue."
fi
