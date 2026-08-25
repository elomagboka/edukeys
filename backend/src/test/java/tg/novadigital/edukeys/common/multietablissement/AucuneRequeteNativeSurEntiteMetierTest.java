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
 * Test statique (T-05, sous-tâche 12) : aucune requête native dans le code
 * applicatif — ni {@code @Query(nativeQuery = true)}, ni
 * {@code EntityManager.createNativeQuery}/{@code createNamedNativeQuery}
 * (docs/adr/0002-multi-etablissement.md, § « Trois angles morts du filtre
 * Hibernate » ; CLAUDE.md, règle 2).
 *
 * <p><b>Portée volontairement plus large qu'« entités {@code EntiteEtablissement}
 * uniquement ».</b> Déterminer statiquement quelle(s) table(s) une chaîne SQL
 * arbitraire touche réellement — alias, jointures, sous-requêtes,
 * concaténation de chaîne — n'est pas fiable : une analyse qui se limiterait
 * aux requêtes « visant » une entité filtrée laisserait passer une requête
 * native mal classée, exactement le faux négatif que ce test doit éviter
 * (une requête native ignore le filtre Hibernate quelle que soit la table
 * qu'elle touche, y compris via une jointure vers une entité métier depuis
 * une entité hors périmètre). Ce test interdit donc <strong>toute</strong>
 * requête native dans {@code src/main/java}, avec une liste blanche
 * explicite et courte pour une exception réellement justifiée — vide
 * aujourd'hui, aucune requête native n'existant dans le code applicatif.</p>
 *
 * <p>Détection sur le contenu complet du fichier (pas ligne par ligne) : une
 * annotation {@code @Query(value = "...", nativeQuery = true)} peut être
 * répartie sur plusieurs lignes, et une analyse ligne par ligne la
 * manquerait selon le formatage — même prudence contre les faux négatifs.</p>
 *
 * <p><b>Garde-fou contre une résolution de sources silencieusement vide</b>
 * (durcissement post-revue, jamais exécuté sous Linux avant cette
 * correction) : {@link #fichiersJavaDeProduction()} exige un minimum de
 * fichiers trouvés, sans quoi il échoue bruyamment plutôt que de laisser le
 * test passer au vert en n'ayant analysé aucun fichier — voir
 * {@link #verifierResolutionDesSources(Path, List)}.</p>
 */
class AucuneRequeteNativeSurEntiteMetierTest {

    /** Liste blanche, explicite et courte : vide, aucune exception justifiée à ce jour. */
    private static final Set<String> FICHIERS_AUTORISES = Set.of();

    private static final Pattern QUERY_NATIVE_ANNOTATION = Pattern.compile("nativeQuery\\s*=\\s*true");
    private static final Pattern APPEL_CREATE_NATIVE_QUERY =
            Pattern.compile("\\bcreateNativeQuery\\s*\\(|\\bcreateNamedNativeQuery\\s*\\(");

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
    @DisplayName("aucun @Query(nativeQuery = true) ni createNativeQuery/createNamedNativeQuery dans le code applicatif")
    void aucuneRequeteNativeDansLeCodeApplicatif() {
        List<String> violations = new ArrayList<>();

        for (Path fichier : fichiersJavaDeProduction()) {
            String nomFichier = fichier.getFileName().toString();
            if (FICHIERS_AUTORISES.contains(nomFichier)) {
                continue;
            }
            String contenu = lireContenu(fichier);
            if (QUERY_NATIVE_ANNOTATION.matcher(contenu).find()
                    || APPEL_CREATE_NATIVE_QUERY.matcher(contenu).find()) {
                violations.add(fichier.toString());
            }
        }

        assertThat(violations)
                .withFailMessage(() -> "Requete(s) native(s) trouvee(s) dans le code applicatif : " + violations + ". "
                        + "Une requete native (@Query(nativeQuery = true), EntityManager.createNativeQuery(...) ou "
                        + "createNamedNativeQuery(...)) echappe au filtre Hibernate multi-etablissement "
                        + "(docs/adr/0002-multi-etablissement.md) et est interdite sur les entites metier "
                        + "(CLAUDE.md, regle 2). Si une exception est reellement justifiee (performance, "
                        + "et le filtrage y est rendu explicite comme l'exige l'ADR), ajoutez le fichier a "
                        + "AucuneRequeteNativeSurEntiteMetierTest.FICHIERS_AUTORISES en motivant pourquoi.")
                .isEmpty();
    }

    private static String lireContenu(Path fichier) {
        try {
            return Files.readString(fichier);
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
                        + Paths.get("").toAbsolutePath() + ") : AucuneRequeteNativeSurEntiteMetierTest ne peut "
                        + "pas analyser le code applicatif. Corrigez racineSourcesDeProduction() avant de faire "
                        + "confiance a un resultat vert de ce test.");
    }

    /**
     * Sans ce garde-fou, une résolution de répertoire qui pointerait
     * silencieusement vers un chemin vide ou presque (une régression future
     * dans {@link #racineSourcesDeProduction()}, un répertoire de travail
     * différent en CI...) ferait passer ce test au vert après n'avoir
     * analysé aucun — ou presque aucun — fichier, sans jamais avoir vérifié
     * la règle qu'il prétend protéger. Message délibérément distinct de
     * celui d'une violation de règle : quelqu'un qui lit ce rouge doit
     * comprendre en une seconde qu'il manque des fichiers analysés, pas
     * qu'une requête native traîne quelque part.
     */
    private static void verifierResolutionDesSources(Path racine, List<Path> fichiers) {
        if (fichiers.size() < SEUIL_MINIMUM_FICHIERS_SOURCE) {
            throw new IllegalStateException(
                    "ECHEC DE RESOLUTION DES SOURCES (pas une violation de la regle testee) : seulement "
                            + fichiers.size() + " fichier(s) .java trouve(s) sous " + racine.toAbsolutePath()
                            + ", en dessous du seuil de securite (" + SEUIL_MINIMUM_FICHIERS_SOURCE + "). "
                            + "AucuneRequeteNativeSurEntiteMetierTest protege une regle non negociable "
                            + "(docs/adr/0002-multi-etablissement.md) : s'il analyse (quasi) zero fichier, il "
                            + "passerait au vert sans avoir rien verifie - corrigez "
                            + "racineSourcesDeProduction() avant de faire confiance a un resultat vert.");
        }
    }
}
