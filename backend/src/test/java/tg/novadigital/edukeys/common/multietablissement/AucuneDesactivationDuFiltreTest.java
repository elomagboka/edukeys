package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test statique (T-05, sous-tâche 12) : personne ne désarme ni ne réarme le
 * filtre Hibernate multi-établissement à la main (ADR-0002, § « Trois angles
 * morts du filtre Hibernate »). Analyse texte des sources plutôt que du
 * bytecode : {@code Session.enableFilter}/{@code disableFilter} sont des
 * appels de méthode explicites dans le code Java lui-même — aucun tissage
 * dynamique, aucune génération de bytecode ne peut les faire apparaître
 * autrement que par écriture directe dans un fichier {@code .java}, donc une
 * analyse texte les attrape aussi sûrement qu'une analyse de bytecode, pour
 * une implémentation bien plus simple à auditer (la liste blanche elle-même
 * doit rester lisible par un humain).
 *
 * <p><b>Liste blanche, explicite et courte</b> : deux emplacements légitimes
 * pour {@code enableFilter}, {@link ArmeurFiltreEtablissement} (l'arme sur
 * chaque session, seul chemin de production) et
 * {@code VerificateurFiltreEtablissement} (vérifie au démarrage que le
 * filtre est bien hérité par toutes les entités {@code EntiteEtablissement} —
 * une session de diagnostic ouverte puis refermée immédiatement, jamais
 * utilisée pour servir une requête applicative). <strong>Aucun</strong>
 * emplacement légitime pour {@code disableFilter} : la désactivation ciblée
 * pour {@code SUPER_ADMIN} évoquée par l'ADR-0002 §5 n'est pas implémentée en
 * désarmant le filtre à l'exécution, mais architecturalement — les quatre
 * entités hors périmètre ({@code Etablissement}, {@code Utilisateur},
 * {@code AffectationEtablissement}, {@code JetonRafraichissement})
 * n'étendent tout simplement jamais {@code EntiteEtablissement}, donc ne
 * portent jamais le {@code @Filter} : rien à désactiver pour elles. Si un
 * jour {@code disableFilter} devenait réellement nécessaire, il faudrait
 * l'ajouter ici, nommément, avec sa justification — pas le découvrir en
 * lisant ce test après coup.</p>
 *
 * <p><b>Garde-fou contre une résolution de sources silencieusement vide</b>
 * (durcissement post-revue, jamais exécuté sous Linux avant cette
 * correction) : {@link #fichiersJavaDeProduction()} exige un minimum de
 * fichiers trouvés, sans quoi il échoue bruyamment plutôt que de laisser les
 * deux tests passer au vert en n'ayant analysé aucun fichier — voir
 * {@link #verifierResolutionDesSources(Path, List)}.</p>
 */
class AucuneDesactivationDuFiltreTest {

    /** Seuls emplacements autorisés à appeler {@code Session.enableFilter(...)}. */
    private static final Set<String> FICHIERS_AUTORISES_ENABLE_FILTER = Set.of(
            "ArmeurFiltreEtablissement.java",
            "VerificateurFiltreEtablissement.java");

    /** Aucun emplacement n'est autorisé à appeler {@code Session.disableFilter(...)}. */
    private static final Set<String> FICHIERS_AUTORISES_DISABLE_FILTER = Set.of();

    private static final Pattern APPEL_ENABLE_FILTER = Pattern.compile("\\benableFilter\\s*\\(");
    private static final Pattern APPEL_DISABLE_FILTER = Pattern.compile("\\bdisableFilter\\s*\\(");

    /**
     * Nombre de fichiers {@code .java} sous {@code src/main/java} à la
     * rédaction de ce test (T-05, sous-tâche 12) : autour de 70. Seuil fixé
     * délibérément loin des deux bornes : nettement au-dessus de zéro — une
     * résolution de chemin qui échoue silencieusement (répertoire de travail
     * inattendu en CI, jamais vérifié sous Linux) renvoie une liste vide, et
     * ce seuil transforme ce zéro en échec bruyant plutôt qu'un vert
     * trompeur — et nettement en dessous du compte actuel, pour ne pas
     * rendre le test fragile à chaque ajout ou suppression normale d'une
     * classe au fil des sprints suivants.
     */
    private static final int SEUIL_MINIMUM_FICHIERS_SOURCE = 30;

    @Test
    @DisplayName("enableFilter n'est appele que depuis la liste blanche explicite")
    void enableFilter_nApparaitQueDansLaListeBlanche() {
        List<String> violations = new ArrayList<>();

        for (Path fichier : fichiersJavaDeProduction()) {
            String nomFichier = fichier.getFileName().toString();
            if (FICHIERS_AUTORISES_ENABLE_FILTER.contains(nomFichier)) {
                continue;
            }
            if (contient(fichier, APPEL_ENABLE_FILTER)) {
                violations.add(fichier.toString());
            }
        }

        assertThat(violations)
                .withFailMessage(() -> "Appel(s) a enableFilter hors de la liste blanche : " + violations + ". "
                        + "Seuls ArmeurFiltreEtablissement (armement legitime) et VerificateurFiltreEtablissement "
                        + "(verification au demarrage) peuvent armer le filtre Hibernate multi-etablissement "
                        + "(docs/adr/0002-multi-etablissement.md). Si ce nouvel appel est reellement legitime, "
                        + "ajoutez le fichier a AucuneDesactivationDuFiltreTest.FICHIERS_AUTORISES_ENABLE_FILTER, "
                        + "en justifiant pourquoi dans un commentaire.")
                .isEmpty();
    }

    @Test
    @DisplayName("disableFilter n'est jamais appele : la derogation SUPER_ADMIN est architecturale, pas un desarmement a l'execution")
    void disableFilter_nApparaitNullePart() {
        List<String> violations = new ArrayList<>();

        for (Path fichier : fichiersJavaDeProduction()) {
            String nomFichier = fichier.getFileName().toString();
            if (FICHIERS_AUTORISES_DISABLE_FILTER.contains(nomFichier)) {
                continue;
            }
            if (contient(fichier, APPEL_DISABLE_FILTER)) {
                violations.add(fichier.toString());
            }
        }

        assertThat(violations)
                .withFailMessage(() -> "Appel(s) a disableFilter trouve(s) : " + violations + ". Le filtre "
                        + "multi-etablissement ne doit jamais etre desarme a l'execution, y compris pour SUPER_ADMIN "
                        + "(docs/adr/0002-multi-etablissement.md, §5) : sa derogation est architecturale (les entites "
                        + "hors perimetre n'etendent jamais EntiteEtablissement), jamais un Session.disableFilter(...) "
                        + "sur des donnees metier. Si un besoin reel et justifie apparait, ajoutez le fichier a "
                        + "AucuneDesactivationDuFiltreTest.FICHIERS_AUTORISES_DISABLE_FILTER en le motivant.")
                .isEmpty();
    }

    private static boolean contient(Path fichier, Pattern motif) {
        try (var lignes = Files.lines(fichier)) {
            return lignes.anyMatch(ligne -> motif.matcher(ligne).find());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> fichiersJavaDeProduction() {
        Path racine = racineSourcesDeProduction();
        List<Path> fichiers;
        try (var flux = Files.walk(racine)) {
            fichiers = flux.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        verifierResolutionDesSources(racine, fichiers);
        return fichiers;
    }

    /**
     * {@code src/main/java} relatif au répertoire de travail du module
     * (Surefire l'exécute depuis {@code backend/}, jamais depuis la racine du
     * dépôt) — avec un repli explicite si jamais ce test était un jour lancé
     * depuis un autre répertoire de travail.
     */
    private static Path racineSourcesDeProduction() {
        Path relatif = Paths.get("src", "main", "java");
        if (Files.isDirectory(relatif)) {
            return relatif;
        }
        Path depuisRacineDepot = Paths.get("backend", "src", "main", "java");
        if (Files.isDirectory(depuisRacineDepot)) {
            return depuisRacineDepot;
        }
        throw new IllegalStateException(
                "ECHEC DE RESOLUTION DES SOURCES (pas une violation de la regle testee) : repertoire des sources "
                        + "de production introuvable (ni src/main/java ni backend/src/main/java depuis "
                        + Paths.get("").toAbsolutePath() + ") : AucuneDesactivationDuFiltreTest ne peut pas "
                        + "analyser le code applicatif. Corrigez racineSourcesDeProduction() avant de faire "
                        + "confiance a un resultat vert de ce test.");
    }

    /**
     * Sans ce garde-fou, une résolution de répertoire qui pointerait
     * silencieusement vers un chemin vide ou presque (une régression future
     * dans {@link #racineSourcesDeProduction()}, un répertoire de travail
     * différent en CI...) ferait passer les deux tests de cette classe au
     * vert après n'avoir analysé aucun — ou presque aucun — fichier, sans
     * jamais avoir vérifié la règle qu'ils prétendent protéger. Message
     * délibérément distinct de celui d'une violation de règle : quelqu'un
     * qui lit ce rouge doit comprendre en une seconde qu'il manque des
     * fichiers analysés, pas qu'un {@code enableFilter} traîne quelque part.
     */
    private static void verifierResolutionDesSources(Path racine, List<Path> fichiers) {
        if (fichiers.size() < SEUIL_MINIMUM_FICHIERS_SOURCE) {
            throw new IllegalStateException(
                    "ECHEC DE RESOLUTION DES SOURCES (pas une violation de la regle testee) : seulement "
                            + fichiers.size() + " fichier(s) .java trouve(s) sous " + racine.toAbsolutePath()
                            + ", en dessous du seuil de securite (" + SEUIL_MINIMUM_FICHIERS_SOURCE + "). "
                            + "AucuneDesactivationDuFiltreTest protege une regle non negociable "
                            + "(docs/adr/0002-multi-etablissement.md) : s'il analyse (quasi) zero fichier, il "
                            + "passerait au vert sans avoir rien verifie - corrigez "
                            + "racineSourcesDeProduction() avant de faire confiance a un resultat vert.");
        }
    }
}
