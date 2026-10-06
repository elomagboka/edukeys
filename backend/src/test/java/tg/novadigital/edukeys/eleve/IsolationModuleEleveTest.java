package tg.novadigital.edukeys.eleve;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import tg.novadigital.edukeys.eleve.service.InscriptionService;

/**
 * CLAUDE.md, règle 1 (US-08) : le module {@code eleve} n'importe que {@code common} et le PAQUET RACINE
 * (les ports) des modules {@code admission}, {@code academique}, {@code identite} et {@code etablissement} —
 * jamais leurs entités, repositories ni services — et aucun module ne dépend d'{@code eleve}.
 */
class IsolationModuleEleveTest {

    private static final String RACINE = "tg.novadigital.edukeys";

    private static JavaClasses classes() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RACINE);
        // Garde contre un import silencieusement vide (ArchUnit rendu inopérant par une version de Java trop récente).
        assertThat(classes.contain(InscriptionService.class)).as("ArchUnit importe le module eleve").isTrue();
        assertThat(classes.size()).isGreaterThan(200);
        return classes;
    }

    @Test
    void eleveNeDependQueDesPortsRacineDesAutresModules() {
        String[] internes = new String[] {
                RACINE + ".admission.domain..", RACINE + ".admission.repository..", RACINE + ".admission.service..",
                RACINE + ".admission.web..", RACINE + ".admission.mapper..",
                RACINE + ".academique.domain..", RACINE + ".academique.repository..", RACINE + ".academique.service..",
                RACINE + ".academique.web..", RACINE + ".academique.mapper..",
                RACINE + ".identite.domain..", RACINE + ".identite.repository..", RACINE + ".identite.service..",
                RACINE + ".identite.security..", RACINE + ".identite.web..", RACINE + ".identite.mapper..",
                RACINE + ".etablissement.domain..", RACINE + ".etablissement.repository..", RACINE + ".etablissement.service..",
                RACINE + ".etablissement.web..", RACINE + ".etablissement.mapper..",
                RACINE + ".finance..", RACINE + ".pedagogie..", RACINE + ".portail..", RACINE + ".reporting.."};

        noClasses().that().resideInAPackage(RACINE + ".eleve..")
                .should().dependOnClassesThat().resideInAnyPackage(internes)
                .because("un module n'importe que common et les interfaces (paquet racine) des autres modules")
                .check(classes());
    }

    @Test
    void aucunAutreModuleNeDependDEleve() {
        noClasses().that().resideOutsideOfPackage(RACINE + ".eleve..")
                .should().dependOnClassesThat().resideInAPackage(RACINE + ".eleve..")
                .check(classes());
    }
}
