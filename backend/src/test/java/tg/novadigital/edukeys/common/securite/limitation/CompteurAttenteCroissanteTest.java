package tg.novadigital.edukeys.common.securite.limitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Tests unitaires du compteur à attente croissante (issue #58) : progression
 * du délai, plafond, remise à zéro après succès, indépendance des clés,
 * expiration.
 */
class CompteurAttenteCroissanteTest {

    private final LimitationDebitProperties.Compteur parametres = new LimitationDebitProperties.Compteur(
            2, Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMinutes(15));

    private final CompteurAttenteCroissante compteur = new CompteurAttenteCroissante(parametres, 1000);

    @Test
    void nImposeAucuneAttente_tantQueLeSeuilDeToleranceNestPasAtteint() {
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");

        assertThat(compteur.dureeAttenteRestante("cle-1")).isZero();
    }

    @Test
    void doubleLeDelaiAChaqueEchecSupplementaire_auDelaDuSeuilDeTolerance() {
        // 2 échecs tolérés, puis 1s, 2s, 4s...
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");

        compteur.enregistrerEchec("cle-1"); // 3e échec : 1s
        Duration apres1erDepassement = compteur.dureeAttenteRestante("cle-1");
        assertThat(apres1erDepassement).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(1));

        compteur.enregistrerEchec("cle-1"); // 4e échec : 2s
        Duration apres2emeDepassement = compteur.dureeAttenteRestante("cle-1");
        assertThat(apres2emeDepassement).isGreaterThan(apres1erDepassement);
        assertThat(apres2emeDepassement).isLessThanOrEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void plafonneLeDelai_apresPlusieursDoublements() {
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");
        for (int i = 0; i < 20; i++) {
            compteur.enregistrerEchec("cle-1");
        }

        assertThat(compteur.dureeAttenteRestante("cle-1")).isLessThanOrEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void remetLeCompteurAZero_apresReinitialisation() {
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");
        assertThat(compteur.dureeAttenteRestante("cle-1")).isPositive();

        compteur.reinitialiser("cle-1");

        assertThat(compteur.dureeAttenteRestante("cle-1")).isZero();

        // Après remise à zéro, il faut de nouveau dépasser le seuil de tolérance
        // avant qu'une attente ne soit imposée : jamais de verrouillage résiduel.
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");
        assertThat(compteur.dureeAttenteRestante("cle-1")).isZero();
    }

    @Test
    void neMelangePasLesCompteursDeDeuxClesDistinctes() {
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");
        compteur.enregistrerEchec("cle-1");

        assertThat(compteur.dureeAttenteRestante("cle-1")).isPositive();
        assertThat(compteur.dureeAttenteRestante("cle-2")).isZero();
    }

    @Test
    void expireLetatDUneCle_apresLaDureeDexpirationConfiguree() {
        LimitationDebitProperties.Compteur parametresExpirationCourte = new LimitationDebitProperties.Compteur(
                0, Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMillis(50));
        CompteurAttenteCroissante compteurExpirationCourte = new CompteurAttenteCroissante(parametresExpirationCourte, 1000);

        compteurExpirationCourte.enregistrerEchec("cle-1");
        assertThat(compteurExpirationCourte.dureeAttenteRestante("cle-1")).isPositive();

        await(() -> compteurExpirationCourte.dureeAttenteRestante("cle-1").isZero(), Duration.ofSeconds(2));
    }

    /**
     * Le code calcule {@code 1L << Math.min(echecsAuDelaDuSeuil - 1, 32)} :
     * sans ce plafonnement de l'exposant, un très grand nombre d'échecs
     * décalerait au-delà de 63 bits et {@code <<} deviendrait périodique en
     * Java (repli silencieux sur un petit nombre, potentiellement négatif)
     * plutôt que de lever une erreur — un attaquant patient pourrait alors
     * "dépasser" le plafond et retomber sur un délai nul. Ce test prouve que,
     * même après un très grand nombre d'échecs, le délai reste positif et ne
     * dépasse jamais {@code delaiPlafond}.
     */
    @Test
    void nePasDeborder_etResterPlafonne_pourUnTresGrandNombreDechecs() {
        for (int i = 0; i < 1_000_000; i++) {
            compteur.enregistrerEchec("cle-debordement");
        }

        Duration attente = compteur.dureeAttenteRestante("cle-debordement");
        assertThat(attente).isPositive();
        assertThat(attente).isLessThanOrEqualTo(Duration.ofSeconds(8));
    }

    /** Caffeine expire paresseusement (à la prochaine opération) : on sonde jusqu'à ce que l'expiration soit visible. */
    private void await(java.util.function.Supplier<Boolean> condition, Duration delaiMax) {
        java.time.Instant limite = java.time.Instant.now().plus(delaiMax);
        while (java.time.Instant.now().isBefore(limite)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertThat(condition.get()).isTrue();
    }
}
