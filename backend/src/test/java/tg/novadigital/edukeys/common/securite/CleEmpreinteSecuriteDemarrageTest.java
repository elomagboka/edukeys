package tg.novadigital.edukeys.common.securite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** M2 : l'application refuse de démarrer sans clé d'empreinte valide (chargement réel par Spring, sans repli). */
class CleEmpreinteSecuriteDemarrageTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(CleEmpreinteSecurite.class);

    @Test
    void demarrageRefuse_quandLaCleEstAbsenteOuTropCourte() {
        runner.run(contexte -> assertThat(contexte).hasFailed());
        runner.withPropertyValues("edukeys.securite.journal.cle-empreinte=trop-courte")
                .run(contexte -> assertThat(contexte).hasFailed());
        runner.withPropertyValues("edukeys.securite.journal.cle-empreinte=" + "a".repeat(31))
                .run(contexte -> assertThat(contexte).hasFailed());
    }

    @Test
    void demarrageAccepte_quandLaCleFaitAuMoins32Octets() {
        runner.withPropertyValues("edukeys.securite.journal.cle-empreinte=" + "a".repeat(32))
                .run(contexte -> assertThat(contexte).hasNotFailed());
    }
}
