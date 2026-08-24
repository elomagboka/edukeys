package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.common.domain.EntiteEtablissement;
import tg.novadigital.edukeys.common.domain.RemplisseurEtablissement;

/**
 * Unitaire des règles R4.1 à R4.5 (sous-tâche 7, T-05). Utilise une entité de
 * test minimale plutôt qu'une entité JPA réelle : {@code @PrePersist} n'exige
 * aucun contexte de persistance pour être exercé, seul le comportement
 * métier du listener est ici sous test.
 */
class RemplisseurEtablissementTest {

    private final RemplisseurEtablissement remplisseur = new RemplisseurEtablissement();
    private final UUID etablissementA = UUID.randomUUID();
    private final UUID etablissementB = UUID.randomUUID();

    /**
     * {@code setEtablissementId} est package-private dans
     * {@code common.domain} : seul {@link RemplisseurEtablissement} l'atteint.
     * Une entité pré-renseignée (R4.3, R4.4) se monte donc par le constructeur
     * {@code protected}, accessible à une sous-classe — exactement le chemin
     * qu'emprunterait une vraie entité métier.
     */
    private static class EntiteDeTest extends EntiteEtablissement {
        EntiteDeTest() {
            super();
        }

        EntiteDeTest(UUID etablissementId) {
            super(etablissementId);
        }
    }

    @AfterEach
    void nettoyer() {
        ContexteEtablissement.courant().ifPresent(p -> ContexteEtablissement.restaurer(null));
    }

    @Test
    void r4_1_remplit_avec_l_etablissement_courant_quand_le_champ_est_nul() {
        EntiteDeTest entite = new EntiteDeTest();
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            remplisseur.remplir(entite);
        }
        assertThat(entite.getEtablissementId()).isEqualTo(etablissementA);
    }

    @Test
    void r4_2_refuse_quand_le_champ_est_nul_et_aucun_contexte_ouvert() {
        EntiteDeTest entite = new EntiteDeTest();
        assertThatThrownBy(() -> remplisseur.remplir(entite))
                .isInstanceOf(ContexteEtablissementAbsentException.class);
        assertThat(entite.getEtablissementId()).isNull();
    }

    @Test
    void r4_3_laisse_inchange_quand_le_champ_egale_le_contexte_courant() {
        EntiteDeTest entite = new EntiteDeTest(etablissementA);
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            remplisseur.remplir(entite);
        }
        assertThat(entite.getEtablissementId()).isEqualTo(etablissementA);
    }

    @Test
    void r4_4_refuse_quand_le_champ_differe_du_contexte_courant() {
        EntiteDeTest entite = new EntiteDeTest(etablissementB);
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            assertThatThrownBy(() -> remplisseur.remplir(entite))
                    .isInstanceOf(EcritureInterEtablissementRefuseeException.class)
                    .hasMessageContaining(etablissementA.toString())
                    .hasMessageContaining(etablissementB.toString());
        }
    }

    @Test
    void r4_4_refuse_quand_le_champ_est_deja_renseigne_sans_contexte_ouvert() {
        EntiteDeTest entite = new EntiteDeTest(etablissementB);
        assertThatThrownBy(() -> remplisseur.remplir(entite))
                .isInstanceOf(EcritureInterEtablissementRefuseeException.class);
    }

    @Test
    void r4_5_traite_etablissement_nil_comme_une_absence_de_contexte() {
        EntiteDeTest entite = new EntiteDeTest();
        try (var portee = ContexteEtablissement.ouvrir(ContexteEtablissement.ETABLISSEMENT_NIL)) {
            assertThatThrownBy(() -> remplisseur.remplir(entite))
                    .isInstanceOf(ContexteEtablissementAbsentException.class);
        }
    }
}
