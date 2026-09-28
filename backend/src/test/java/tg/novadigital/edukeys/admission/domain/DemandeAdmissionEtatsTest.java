package tg.novadigital.edukeys.admission.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;

/**
 * Machine à états de {@link DemandeAdmission#changerStatut} (US-07, critère 13
 * de la couverture US-06) : transitions permises et interdites.
 */
class DemandeAdmissionEtatsTest {

    private DemandeAdmission nouvelleDemande() {
        return new DemandeAdmission(
                UUID.randomUUID(), "PRE-2026-000001", "CODESUIVI0000000000000001", UUID.randomUUID(), UUID.randomUUID(), null,
                "Nom", "Prenoms", LocalDate.of(2015, 1, 1), "Lomé", "M", "TG", null,
                "Responsable", "Nom", LienResponsable.PERE, "+22890000000", null,
                CanalAdmission.PUBLIC, Instant.now(), Instant.now(), "hash");
    }

    @Test
    void doitAccepterTransition_deEnAttenteVersAcceptee() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.ACCEPTEE, "motif", UUID.randomUUID(), Instant.now());
        assertThat(demande.getStatut()).isEqualTo(StatutAdmission.ACCEPTEE);
        assertThat(demande.estModifiable()).isFalse();
    }

    @Test
    void doitAccepterTransition_deEnAttenteVersRefusee() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.REFUSEE, "motif", UUID.randomUUID(), Instant.now());
        assertThat(demande.getStatut()).isEqualTo(StatutAdmission.REFUSEE);
    }

    @Test
    void doitAccepterTransition_deEnAttenteVersListeAttente() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.LISTE_ATTENTE, "motif", UUID.randomUUID(), Instant.now());
        assertThat(demande.getStatut()).isEqualTo(StatutAdmission.LISTE_ATTENTE);
        assertThat(demande.estModifiable()).isFalse();
    }

    @Test
    void doitAccepterTransition_deListeAttenteVersAcceptee() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.LISTE_ATTENTE, "motif", UUID.randomUUID(), Instant.now());
        demande.changerStatut(StatutAdmission.ACCEPTEE, "motif final", UUID.randomUUID(), Instant.now());
        assertThat(demande.getStatut()).isEqualTo(StatutAdmission.ACCEPTEE);
    }

    @Test
    void doitAccepterTransition_deListeAttenteVersRefusee() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.LISTE_ATTENTE, "motif", UUID.randomUUID(), Instant.now());
        demande.changerStatut(StatutAdmission.REFUSEE, "motif final", UUID.randomUUID(), Instant.now());
        assertThat(demande.getStatut()).isEqualTo(StatutAdmission.REFUSEE);
    }

    @Test
    void doitRejeterTransition_dEnAttenteVersAnnulee_transitionNonAutorisee() {
        DemandeAdmission demande = nouvelleDemande();
        assertThatThrownBy(() -> demande.changerStatut(StatutAdmission.ANNULEE, "motif", UUID.randomUUID(), Instant.now()))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_TRANSITION_INVALIDE));
    }

    @Test
    void doitRejeterTransition_depuisUnStatutFinalAcceptee() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.ACCEPTEE, "motif", UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> demande.changerStatut(StatutAdmission.REFUSEE, "motif", UUID.randomUUID(), Instant.now()))
                .isInstanceOf(RegleMetierViolee.class);
    }

    /**
     * REFUSEE n'est plus finale (amendement produit US-07) : un refus est
     * corrigible, ex. clic erroné. Seuls ACCEPTEE et ANNULEE sont finaux.
     */
    @Test
    void doitAccepterTransition_deRefuseeVersListeAttente() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.REFUSEE, "motif", UUID.randomUUID(), Instant.now());
        demande.changerStatut(StatutAdmission.LISTE_ATTENTE, "correction", UUID.randomUUID(), Instant.now());
        assertThat(demande.getStatut()).isEqualTo(StatutAdmission.LISTE_ATTENTE);
    }

    @Test
    void doitAccepterTransition_deRefuseeVersAcceptee() {
        DemandeAdmission demande = nouvelleDemande();
        demande.changerStatut(StatutAdmission.REFUSEE, "motif", UUID.randomUUID(), Instant.now());
        demande.changerStatut(StatutAdmission.ACCEPTEE, "correction", UUID.randomUUID(), Instant.now());
        assertThat(demande.getStatut()).isEqualTo(StatutAdmission.ACCEPTEE);
    }


    @Test
    void doitRejeterTransition_deEnAttenteVersEnAttente_transitionReflexiveNonAutorisee() {
        DemandeAdmission demande = nouvelleDemande();
        assertThatThrownBy(() -> demande.changerStatut(StatutAdmission.EN_ATTENTE, "motif", UUID.randomUUID(), Instant.now()))
                .isInstanceOf(RegleMetierViolee.class);
    }

    @Test
    void estModifiable_seulementQuandEnAttente() {
        DemandeAdmission demande = nouvelleDemande();
        assertThat(demande.estModifiable()).isTrue();
        demande.changerStatut(StatutAdmission.LISTE_ATTENTE, "motif", UUID.randomUUID(), Instant.now());
        assertThat(demande.estModifiable()).isFalse();
    }
}
