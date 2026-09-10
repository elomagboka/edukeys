package tg.novadigital.edukeys.common.securite.reseau;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Vérifie que l'IP retenue est celle déposée par le proxy de confiance, et
 * jamais celle que le client s'attribue lui-même (issue #58).
 */
class ExtracteurAdresseIpClienteTest {

    private static final String ADRESSE_DISTANTE = "10.0.0.1";

    @Test
    void ignoreLaValeurForgeeParLeClient_etRetientCelleDeposeeParLeProxyDeConfiance() {
        // Le client écrit « 1.2.3.4 » ; l'edge Render ajoute derrière l'IP
        // réellement observée. Avec un proxy de confiance, seule la dernière
        // compte — c'est exactement ce que ForwardedHeaderFilter ne faisait pas.
        String ip = ExtracteurAdresseIpCliente.extraire("1.2.3.4, 41.207.0.9", ADRESSE_DISTANTE, 1);

        assertThat(ip).isEqualTo("41.207.0.9");
    }

    @Test
    void ignoreUneListeEntiereForgee_quelQueSoitLeNombreDelementsInventes() {
        String forge = "1.1.1.1, 2.2.2.2, 3.3.3.3, 4.4.4.4, 41.207.0.9";

        assertThat(ExtracteurAdresseIpCliente.extraire(forge, ADRESSE_DISTANTE, 1)).isEqualTo("41.207.0.9");
    }

    @Test
    void retientLeNiemeElementEnPartantDeLaDroite_quandPlusieursProxysSontDeConfiance() {
        String enTete = "1.2.3.4, 41.207.0.9, 10.1.1.1";

        assertThat(ExtracteurAdresseIpCliente.extraire(enTete, ADRESSE_DISTANTE, 2)).isEqualTo("41.207.0.9");
    }

    @Test
    void retombeSurAdresseDistante_quandLEnTeteEstAbsent() {
        assertThat(ExtracteurAdresseIpCliente.extraire(null, ADRESSE_DISTANTE, 1)).isEqualTo(ADRESSE_DISTANTE);
        assertThat(ExtracteurAdresseIpCliente.extraire("   ", ADRESSE_DISTANTE, 1)).isEqualTo(ADRESSE_DISTANTE);
    }

    /**
     * Développement local : aucun proxy devant l'application, donc aucun
     * élément d'en-tête n'est digne de confiance — on retombe sur l'adresse
     * de la connexion plutôt que d'échouer ou de croire le client.
     */
    @Test
    void retombeSurAdresseDistante_quandLEnTetePorteMoinsDelementsQueDeProxysAttendus() {
        assertThat(ExtracteurAdresseIpCliente.extraire("1.2.3.4", ADRESSE_DISTANTE, 2)).isEqualTo(ADRESSE_DISTANTE);
    }

    @Test
    void tolereLesEspacesEtLesElementsVides() {
        assertThat(ExtracteurAdresseIpCliente.extraire("  1.2.3.4 ,, 41.207.0.9  ", ADRESSE_DISTANTE, 1))
                .isEqualTo("41.207.0.9");
    }
}
