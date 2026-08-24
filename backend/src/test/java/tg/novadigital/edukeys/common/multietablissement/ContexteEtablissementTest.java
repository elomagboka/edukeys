package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ContexteEtablissementTest {

    private final UUID etablissementA = UUID.randomUUID();
    private final UUID etablissementB = UUID.randomUUID();

    @AfterEach
    void nettoyer() {
        // Sécurité : un test qui échouerait avant close() ne doit pas polluer les suivants.
        ContexteEtablissement.courant().ifPresent(p -> ContexteEtablissement.restaurer(null));
    }

    @Test
    void aucun_contexte_ouvert_par_defaut() {
        assertThat(ContexteEtablissement.courant()).isEmpty();
        assertThatThrownBy(ContexteEtablissement::exigerEtablissementId)
                .isInstanceOf(ContexteEtablissementAbsentException.class);
    }

    @Test
    void ouvrir_expose_l_etablissement_courant() {
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            assertThat(ContexteEtablissement.exigerEtablissementId()).isEqualTo(etablissementA);
            assertThat(ContexteEtablissement.courant())
                    .hasValue(new PerimetreEtablissement(etablissementA, PerimetreEtablissement.Origine.EXPLICITE));
        }
    }

    @Test
    void close_restaure_l_absence_de_contexte() {
        var portee = ContexteEtablissement.ouvrir(etablissementA);
        portee.close();

        assertThat(ContexteEtablissement.courant()).isEmpty();
    }

    @Test
    void contextes_imbriques_restaurent_le_precedent_a_la_fermeture() {
        try (var porteeA = ContexteEtablissement.ouvrir(etablissementA)) {
            try (var porteeB = ContexteEtablissement.ouvrir(etablissementB)) {
                assertThat(ContexteEtablissement.exigerEtablissementId()).isEqualTo(etablissementB);
            }
            // La fermeture de la portée imbriquée restaure A, pas l'absence de contexte.
            assertThat(ContexteEtablissement.exigerEtablissementId()).isEqualTo(etablissementA);
        }
        assertThat(ContexteEtablissement.courant()).isEmpty();
    }

    @Test
    void fermeture_repetee_de_la_meme_portee_est_sans_effet() {
        var portee = ContexteEtablissement.ouvrir(etablissementA);
        portee.close();
        portee.close();

        assertThat(ContexteEtablissement.courant()).isEmpty();
    }

    @Test
    void origine_bascule_super_admin_est_conservee() {
        try (var portee = ContexteEtablissement.ouvrir(etablissementA, PerimetreEtablissement.Origine.BASCULE_SUPER_ADMIN)) {
            assertThat(ContexteEtablissement.courant())
                    .hasValueSatisfying(p -> assertThat(p.origine()).isEqualTo(PerimetreEtablissement.Origine.BASCULE_SUPER_ADMIN));
        }
    }
}
