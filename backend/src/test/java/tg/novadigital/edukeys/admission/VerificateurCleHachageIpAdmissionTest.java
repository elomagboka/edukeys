package tg.novadigital.edukeys.admission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I7 (revue US-06) : une clé HMAC par défaut rend le hachage des IP
 * réversible en pratique (l'espace IPv4 se parcourt en quelques minutes).
 * Hors local/test, le démarrage doit donc échouer plutôt que tourner avec.
 */
class VerificateurCleHachageIpAdmissionTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "changez-moi"})
    void refuseLeDemarrage_enProduction_sansCleExplicite(String cle) {
        var verificateur = verificateur(cle, "prod");

        assertThatThrownBy(() -> verificateur.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EDUKEYS_ADMISSION_SEL_HACHAGE_IP");
    }

    @Test
    void refuseLeDemarrage_sansAucunProfilActif() {
        var verificateur = verificateur("changez-moi");

        assertThatThrownBy(() -> verificateur.run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accepteLeDemarrage_enProduction_avecUneVraieCle() {
        var verificateur = verificateur("7f3c9a1e-une-vraie-cle-de-production", "prod");

        assertThatCode(() -> verificateur.run(null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    void accepteLaCleParDefaut_enLocalEtEnTest(String profil) {
        var verificateur = verificateur("changez-moi", profil);

        assertThatCode(() -> verificateur.run(null)).doesNotThrowAnyException();
    }

    private static VerificateurCleHachageIpAdmission verificateur(String cle, String... profils) {
        var proprietes = new AdmissionProperties();
        proprietes.setSelHachageIp(cle);
        var environnement = new MockEnvironment();
        environnement.setActiveProfiles(profils);
        return new VerificateurCleHachageIpAdmission(proprietes, environnement);
    }
}
