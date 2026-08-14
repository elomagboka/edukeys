#!/usr/bin/env bash
# Crée les jalons, étiquettes et issues GitHub d'Edukeys.
#
# À lancer UNE FOIS, depuis Git Bash, à la racine du dépôt :
#     bash scripts/creer-issues.sh
#
# Prérequis : gh installé et authentifié (gh auth login), dépôt distant créé.
# Le script est réexécutable : il ignore ce qui existe déjà.
#
# Rappel (voir GUIDE-CLAUDE-CODE.md) : les issues portent le SUIVI, pas la
# spécification. Le contenu détaillé reste dans docs/backlog.md et
# docs/SPRINT-0.md, versionnés avec le code.

set -euo pipefail
cd "$(dirname "$0")/.."

command -v gh >/dev/null || { echo "gh introuvable. winget install GitHub.cli"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Non authentifié. Lance : gh auth login"; exit 1; }

DEPOT=$(gh repo view --json nameWithOwner -q .nameWithOwner)
echo "Dépôt : $DEPOT"
read -rp "Continuer ? [o/N] " reponse
[[ "$reponse" == "o" ]] || exit 0

# ---------------------------------------------------------------- étiquettes
echo
echo "== Étiquettes =="
creer_label() {
  gh label create "$1" --color "$2" --description "$3" --force >/dev/null 2>&1 \
    && echo "  $1"
}
creer_label "type:tache"       "0e8a16" "Tâche technique du Sprint 0"
creer_label "type:us"          "1d76db" "User story du backlog"
creer_label "epic:config"      "c5def5" "Configuration et paramétrage"
creer_label "epic:admission"   "c5def5" "Admission et inscription"
creer_label "epic:pedagogie"   "c5def5" "Vie scolaire et pédagogie"
creer_label "epic:finance"     "c5def5" "Gestion financière"
creer_label "epic:portail"     "c5def5" "Portails et communication"
creer_label "epic:reporting"   "c5def5" "Pilotage et reporting"
creer_label "priorite:must"    "b60205" "Indispensable"
creer_label "priorite:should"  "fbca04" "Souhaitable"
creer_label "priorite:could"   "d4c5f9" "Optionnel"
creer_label "bloque"           "e11d21" "Bloqué par une dépendance"

# ------------------------------------------------------------------ jalons
# gh n'a pas de commande milestone : on passe par l'API.
echo
echo "== Jalons =="
jalons_existants=$(gh api "repos/$DEPOT/milestones?state=all" -q '.[].title')
creer_jalon() {
  if grep -qxF "$1" <<< "$jalons_existants"; then
    echo "  $1 (existe)"
  else
    gh api "repos/$DEPOT/milestones" -f title="$1" -f description="$2" >/dev/null
    echo "  $1"
  fi
}
creer_jalon "Sprint 0"  "Socle technique + US-00"
creer_jalon "Sprint 1"  "Référentiel académique"
creer_jalon "Sprint 2"  "Admission"
creer_jalon "Sprint 3"  "Dossier élève"
creer_jalon "Sprint 4"  "Vie scolaire"
creer_jalon "Sprint EDT" "Emplois du temps (US-14)"
creer_jalon "Sprint 5"  "Présences et devoirs"
creer_jalon "Sprint 6"  "Évaluation"
creer_jalon "Sprint 7"  "Facturation"
creer_jalon "Sprint 8"  "Trésorerie"
creer_jalon "Sprint 9"  "Portails"
creer_jalon "Sprint 10" "Communication"
creer_jalon "Sprint 11" "Pilotage"

# ------------------------------------------------------------------ issues
titres_existants=$(gh issue list --limit 500 --state all --json title -q '.[].title')

creer_issue() {   # $1 titre  $2 corps  $3 jalon  $4... étiquettes
  local titre="$1" corps="$2" jalon="$3"; shift 3
  if grep -qxF "$titre" <<< "$titres_existants"; then
    echo "  (existe) $titre"; return
  fi
  local args=(--title "$titre" --body "$corps" --milestone "$jalon")
  for l in "$@"; do args+=(--label "$l"); done
  gh issue create "${args[@]}" >/dev/null
  echo "  $titre"
}

echo
echo "== Tâches du Sprint 0 =="
while IFS='|' read -r ref libelle; do
  [[ -z "$ref" ]] && continue
  creer_issue "$ref — $libelle" \
    "Spécification : \`docs/SPRINT-0.md\`, section **$ref**.

Critère de fin et détail des livrables dans le fichier — ce ticket sert au
suivi, pas à la spécification.

Décisions applicables : \`docs/adr/\`" \
    "Sprint 0" "type:tache"
done << 'FIN'
T-00|Figer les décisions
T-01|Squelette qui démarre
T-02|Socle commun
T-03|Pagination et recherche standardisées
T-04|Sécurité et RBAC
T-05|Contexte multi-établissement
T-06|Audit et historisation
T-07|Documentation d'API
T-08|Socle de tests
T-09|Intégration et déploiement continus
T-10|US-00 en tranche verticale
T-11|Socle frontend
FIN

echo
echo "== Tâches du Sprint EDT =="
while IFS='|' read -r ref libelle; do
  [[ -z "$ref" ]] && continue
  creer_issue "$ref — $libelle" \
    "Spécification : \`docs/SPRINT-EDT.md\`, section **$ref**.

Rattaché à US-14. Contraintes de sites : \`docs/adr/0005-sites-et-annexes.md\`" \
    "Sprint EDT" "type:tache" "epic:pedagogie"
done << 'FIN'
E-01|Modèle et référentiel
E-02|Moteur de contraintes
E-03|Recherche de créneaux disponibles
E-04|API emplois du temps
E-05|Interface de construction
E-06|Consultation et diffusion
E-07|Tests sur données réelles
FIN

echo
echo "== User stories =="
while IFS='|' read -r ident sprint titre; do
  [[ -z "$ident" ]] && continue
  num=${ident#US-}; num=$((10#$num))
  if   (( num <= 5  )); then epic="epic:config"
  elif (( num <= 12 )); then epic="epic:admission"
  elif (( num <= 20 )); then epic="epic:pedagogie"
  elif (( num <= 26 )); then epic="epic:finance"
  elif (( num <= 32 )); then epic="epic:portail"
  else                       epic="epic:reporting"; fi

  case " US-11 US-24 US-25 US-29 US-34 US-35 " in
    *" $ident "*) prio="priorite:should" ;;
    *) [[ "$ident" == "US-32" ]] && prio="priorite:could" || prio="priorite:must" ;;
  esac

  [[ "$sprint" == "EDT" ]] && jalon="Sprint EDT" || jalon="Sprint $sprint"

  creer_issue "$ident — $titre" \
    "Spécification et critères d'acceptation : \`docs/backlog.md\`, section **$ident**.

Pour l'implémenter avec Claude Code :
\`\`\`
/us ${ident#US-}
\`\`\`

Définition de terminé : voir \`CLAUDE.md\`." \
    "$jalon" "type:us" "$epic" "$prio"
done < scripts/us.txt

echo
echo "Terminé. Vue d'ensemble : gh issue list --milestone 'Sprint 0'"
