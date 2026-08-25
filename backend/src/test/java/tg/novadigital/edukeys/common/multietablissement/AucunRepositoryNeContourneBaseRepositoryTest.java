package tg.novadigital.edukeys.common.multietablissement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Test statique (durcissement post-revue T-05) : aucune interface, ni dans le
 * code de production ni dans le code de test, n'étend directement
 * {@code JpaRepository}, {@code JpaSpecificationExecutor} ou
 * {@code CrudRepository} — le seul socle autorisé pour un repository métier
 * est {@code BaseRepository} (CLAUDE.md, règle 4).
 *
 * <p><b>Pourquoi ArchUnit plutôt qu'une analyse texte comme
 * {@link AucuneDesactivationDuFiltreTest}</b> : ici la règle porte sur la
 * hiérarchie de types (« quelle interface étend quoi »), qu'ArchUnit résout
 * sur le bytecode importé — donc insensible à un import statique, un alias,
 * ou un renommage de méthode qui tromperait une recherche textuelle.</p>
 *
 * <p><b>Pourquoi cela ferme le trou trouvé en revue</b> :
 * {@code IsolationEtablissementTest} (b) découvre les repositories via
 * {@code ApplicationContext.getBeansOfType(BaseRepository.class)} — un
 * repository qui écrit {@code extends JpaRepository<Entite, UUID>} au lieu de
 * {@code BaseRepository} n'est tout simplement pas un {@code BaseRepository},
 * donc n'entrait jamais dans cette boucle avant sa mise à jour dans le cadre
 * de ce durcissement. Cette règle ArchUnit est indépendante de ce mécanisme :
 * elle attrape l'interface elle-même, avant même qu'un bean Spring existe,
 * qu'un test l'exerce, ou qu'un contexte applicatif démarre.</p>
 *
 * <p><b>Portée volontairement étendue à {@code src/test/java}</b> : le
 * JOURNAL (2026-08-24, T-05) documente un incident où un test unitaire
 * substituant un champ {@code static} partagé (
 * {@code ContexteEtablissement.entityManagerFactory}) a contaminé toute la
 * campagne de tests suivante — la preuve que du code de test peut casser une
 * garantie que le code de production tient par ailleurs. Un repository de
 * test qui contournerait {@code BaseRepository} (par exemple pour nettoyer
 * des données entre tests via {@code deleteAll()}) réintroduirait
 * silencieusement une suppression physique ou une désactivation de filtre
 * dans le cycle de vie des tests, sans jamais toucher la production — d'où le
 * même contrôle sur les deux arborescences.</p>
 */
class AucunRepositoryNeContourneBaseRepositoryTest {

    private static final String MESSAGE_EXPLICATIF =
            "Tout repository Spring Data du projet Edukeys doit etendre "
                    + "tg.novadigital.edukeys.common.repository.BaseRepository, jamais JpaRepository, "
                    + "JpaSpecificationExecutor ou CrudRepository directement (CLAUDE.md, regle 4). Ces "
                    + "interfaces exposent des methodes de suppression physique (delete, deleteAll, "
                    + "deleteAllInBatch...) et, pour JpaSpecificationExecutor, une suppression en masse par "
                    + "Specification traduite directement en DELETE SQL : les deux court-circuitent l'audit "
                    + "Envers et le filtre Hibernate multi-etablissement. Faites etendre l'interface fautive de "
                    + "BaseRepository a la place.";

    /**
     * Garde-fou identique en esprit à celui d'{@link AucuneDesactivationDuFiltreTest} :
     * si l'import ArchUnit résout silencieusement zéro classe (mauvais
     * répertoire de travail, filtre d'import trop restrictif...), la règle
     * ci-dessous passerait au vert sans avoir analysé quoi que ce soit.
     */
    private static final int SEUIL_MINIMUM_CLASSES_IMPORTEES = 30;

    @Test
    void aucuneInterfaceNeContourneBaseRepository() {
        // Import par repertoires de classes compilees plutot que
        // importPackages(...) : sous Surefire, le classpath de test est
        // parfois expose via un jar manifest-only (useManifestOnlyJar), dont
        // le Class-Path du manifeste n'est pas resolu par la recherche de
        // packages d'ArchUnit (basee sur ClassLoader.getResources) - elle
        // importerait alors silencieusement zero classe. Importer les
        // repertoires target/classes et target/test-classes directement
        // contourne cette dependance au mecanisme de classpath du lanceur.
        JavaClasses classes = new ClassFileImporter().importPaths(cheminsClassesCompilees());

        List<JavaClass> classesImportees = classes.stream().toList();
        if (classesImportees.size() < SEUIL_MINIMUM_CLASSES_IMPORTEES) {
            throw new IllegalStateException(
                    "ECHEC DE RESOLUTION DES SOURCES (pas une violation de la regle testee) : seulement "
                            + classesImportees.size() + " classe(s) importee(s) par ArchUnit sous le package "
                            + "tg.novadigital.edukeys, en dessous du seuil de securite ("
                            + SEUIL_MINIMUM_CLASSES_IMPORTEES + "). AucunRepositoryNeContourneBaseRepositoryTest "
                            + "protege une regle non negociable (CLAUDE.md, regle 4) : s'il importe (quasi) zero "
                            + "classe, il passerait au vert sans avoir rien verifie.");
        }

        ArchRule regle = noClasses()
                .that()
                .resideInAPackage("tg.novadigital.edukeys..")
                .and()
                .areInterfaces()
                .should()
                .beAssignableTo(org.springframework.data.jpa.repository.JpaRepository.class)
                .orShould()
                .beAssignableTo(org.springframework.data.jpa.repository.JpaSpecificationExecutor.class)
                .orShould()
                .beAssignableTo(org.springframework.data.repository.CrudRepository.class)
                .as("aucune interface n'etend JpaRepository, JpaSpecificationExecutor ou CrudRepository "
                        + "(seul BaseRepository est autorise)")
                .because(MESSAGE_EXPLICATIF);

        regle.check(classes);
    }

    /**
     * {@code target/classes} et {@code target/test-classes}, relatifs au
     * répertoire de travail du module (Surefire l'exécute depuis
     * {@code backend/}) — avec repli explicite si jamais ce test était lancé
     * depuis un autre répertoire de travail. Même logique que
     * {@code AucuneDesactivationDuFiltreTest.racineSourcesDeProduction()}.
     */
    private static List<Path> cheminsClassesCompilees() {
        Path racineModule = Files.isDirectory(Paths.get("target")) ? Paths.get("") : Paths.get("backend");
        Path classesMain = racineModule.resolve(Paths.get("target", "classes"));
        Path classesTest = racineModule.resolve(Paths.get("target", "test-classes"));

        if (!Files.isDirectory(classesMain) || !Files.isDirectory(classesTest)) {
            throw new IllegalStateException(
                    "ECHEC DE RESOLUTION DES SOURCES (pas une violation de la regle testee) : "
                            + classesMain.toAbsolutePath() + " ou " + classesTest.toAbsolutePath()
                            + " introuvable(s). AucunRepositoryNeContourneBaseRepositoryTest ne peut pas importer "
                            + "les classes compilees - executez au moins 'mvn test-compile' avant ce test, et "
                            + "corrigez cheminsClassesCompilees() si le repertoire de travail a change.");
        }
        return List.of(classesMain.toAbsolutePath(), classesTest.toAbsolutePath());
    }
}
