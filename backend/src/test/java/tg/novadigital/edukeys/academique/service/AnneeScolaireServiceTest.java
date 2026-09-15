package tg.novadigital.edukeys.academique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.repository.AnneeScolaireRepository;
import tg.novadigital.edukeys.academique.web.CreerAnneeScolaireRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierAnneeScolaireRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Tests unitaires du service {@link AnneeScolaireService} : règles R1 à R12
 * de la spec US-01 (module {@code academique}), une par une. Le repository et
 * l'{@link EntityManager} sont mockés — le garde-fou de correction pour R5 /
 * R8 (contrainte de base) est vérifié séparément par un test d'intégration
 * sur Testcontainers, pas ici.
 */
class AnneeScolaireServiceTest {

    private AnneeScolaireRepository repository;
    private EntityManager entityManager;
    private AnneeScolaireService service;
    private UUID etablissementId;
    private PorteeEtablissement portee;

    @BeforeEach
    void configurer() {
        repository = mock(AnneeScolaireRepository.class);
        entityManager = mock(EntityManager.class);
        service = new AnneeScolaireService(repository, entityManager);
        etablissementId = UUID.randomUUID();
        portee = ContexteEtablissement.ouvrir(etablissementId);

        when(repository.save(any(AnneeScolaire.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.existsByEtablissementIdAndLibelleAndActifTrue(any(), anyString())).thenReturn(false);
        when(repository.rechercherChevauchements(any(), any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void fermerContexte() {
        portee.close();
    }

    private AnneeScolaire anneeAvecId(UUID id, String libelle, LocalDate debut, LocalDate fin, StatutAnneeScolaire statut) {
        AnneeScolaire annee = new AnneeScolaire(etablissementId, libelle, debut, fin);
        ReflectionTestUtils.setField(annee, "id", id);
        ReflectionTestUtils.setField(annee, "statut", statut);
        return annee;
    }

    // ------------------------------------------------------------------
    // R1 : dateFin > dateDebut
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandDateFinAnterieureADateDebut() {
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1), null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_DATES_INCOHERENTES);
    }

    @Test
    void doitRejeterCreation_quandDateFinEgaleADateDebut() {
        LocalDate meme = LocalDate.of(2026, 9, 1);
        CreerAnneeScolaireRequestDto requete = new CreerAnneeScolaireRequestDto(meme, meme, null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_DATES_INCOHERENTES);
    }

    // ------------------------------------------------------------------
    // R2 : durée entre 30 et 500 jours
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandDureeInferieureAMinimum() {
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_DUREE_INVALIDE);
    }

    @Test
    void doitRejeterCreation_quandDureeSuperieureAMaximum() {
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_DUREE_INVALIDE);
    }

    @Test
    void doitAccepterCreation_quandDureeExactementAuxBornes() {
        // Borne basse : exactement 30 jours.
        CreerAnneeScolaireRequestDto borneBasse =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), "BORNE-BASSE");
        AnneeScolaire creeeBasse = service.creer(borneBasse);
        assertThat(creeeBasse.getStatut()).isEqualTo(StatutAnneeScolaire.PREPARATION);

        // Borne haute : exactement 500 jours.
        CreerAnneeScolaireRequestDto borneHaute = new CreerAnneeScolaireRequestDto(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(500), "BORNE-HAUTE");
        AnneeScolaire creeeHaute = service.creer(borneHaute);
        assertThat(creeeHaute.getStatut()).isEqualTo(StatutAnneeScolaire.PREPARATION);
    }

    // ------------------------------------------------------------------
    // DELTA 1 / R3 : libellé libre (absent -> généré, fourni -> trim() seul)
    // ------------------------------------------------------------------

    @Test
    void doitGenererLibelleAuFormatAnneeAnnee_quandLibelleAbsent() {
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), null);

        AnneeScolaire creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("2026-2027");
    }

    @Test
    void doitAccepterLibelleFourniTelQuel_sansImposerLeFormatAnneeAnnee() {
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), "2026/2027");

        AnneeScolaire creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("2026/2027");
    }

    @Test
    void doitGenererLibelle_quandLibelleFourniEstBlanc() {
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), "   ");

        AnneeScolaire creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("2026-2027");
    }

    @Test
    void doitConserverLibelleExistant_quandModificationDeDatesSansLibelle() {
        UUID id = UUID.randomUUID();
        AnneeScolaire existante = anneeAvecId(id, "Rentrée 2026", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.PREPARATION);
        when(repository.findById(id)).thenReturn(Optional.of(existante));

        ModifierAnneeScolaireRequestDto requete =
                new ModifierAnneeScolaireRequestDto(LocalDate.of(2026, 9, 15), LocalDate.of(2027, 7, 20), null);

        AnneeScolaire modifiee = service.modifier(id, requete);

        assertThat(modifiee.getLibelle()).isEqualTo("Rentrée 2026");
        verify(repository, never()).existsByEtablissementIdAndLibelleAndActifTrue(any(), any());
    }

    // ------------------------------------------------------------------
    // R4 : libellé unique par établissement parmi les années actives
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandLibelleDejaUtiliseParUneAnneeActive() {
        when(repository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, "2026-2027")).thenReturn(true);
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), "2026-2027");

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_LIBELLE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R5 : aucun chevauchement de plage avec une autre année active
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandPeriodeChevaucheUneAutreAnneeActive() {
        AnneeScolaire existante = anneeAvecId(UUID.randomUUID(), "2025-2026",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 7, 15), StatutAnneeScolaire.ACTIVE);
        when(repository.rechercherChevauchements(eq(etablissementId), any(), any(), isNull()))
                .thenReturn(List.of(existante));

        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 6, 1), LocalDate.of(2027, 6, 1), "2026-2027");

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_PERIODE_CHEVAUCHANTE);
    }

    // ------------------------------------------------------------------
    // R6 : une année est créée en PREPARATION
    // ------------------------------------------------------------------

    @Test
    void doitCreerEnPreparation() {
        CreerAnneeScolaireRequestDto requete =
                new CreerAnneeScolaireRequestDto(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), "2026-2027");

        AnneeScolaire creee = service.creer(requete);

        assertThat(creee.getStatut()).isEqualTo(StatutAnneeScolaire.PREPARATION);
    }

    // ------------------------------------------------------------------
    // R7 : transitions autorisées / refusées
    // ------------------------------------------------------------------

    @Test
    void doitRejeterActivation_quandAnneeDejaActive() {
        UUID id = UUID.randomUUID();
        AnneeScolaire cible = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.ACTIVE);
        when(repository.findById(id)).thenReturn(Optional.of(cible));

        assertThatThrownBy(() -> service.activer(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_TRANSITION_INVALIDE);
    }

    @Test
    void doitRejeterActivation_quandAnneeDejaCloturee() {
        UUID id = UUID.randomUUID();
        AnneeScolaire cible = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.CLOTUREE);
        when(repository.findById(id)).thenReturn(Optional.of(cible));

        assertThatThrownBy(() -> service.activer(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_TRANSITION_INVALIDE);
    }

    @Test
    void doitRejeterCloture_quandAnneeEnPreparation() {
        UUID id = UUID.randomUUID();
        AnneeScolaire cible = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.PREPARATION);
        when(repository.findById(id)).thenReturn(Optional.of(cible));

        assertThatThrownBy(() -> service.cloturer(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_TRANSITION_INVALIDE);
    }

    @Test
    void doitRejeterCloture_quandAnneeDejaCloturee() {
        UUID id = UUID.randomUUID();
        AnneeScolaire cible = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.CLOTUREE);
        when(repository.findById(id)).thenReturn(Optional.of(cible));

        assertThatThrownBy(() -> service.cloturer(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_TRANSITION_INVALIDE);
    }

    // ------------------------------------------------------------------
    // R8 : bascule transactionnelle d'activation
    // ------------------------------------------------------------------

    @Test
    void doitCloturerLancienneAnneeActive_puisActiverLaCible() {
        UUID idAncienneActive = UUID.randomUUID();
        UUID idCible = UUID.randomUUID();
        AnneeScolaire ancienneActive = anneeAvecId(idAncienneActive, "2025-2026",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 7, 15), StatutAnneeScolaire.ACTIVE);
        AnneeScolaire cible = anneeAvecId(idCible, "2026-2027",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.PREPARATION);

        when(repository.findById(idCible)).thenReturn(Optional.of(cible));
        when(repository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.of(ancienneActive));

        AnneeScolaire resultat = service.activer(idCible);

        assertThat(resultat.getStatut()).isEqualTo(StatutAnneeScolaire.ACTIVE);
        assertThat(resultat.getDateActivation()).isNotNull();
        assertThat(ancienneActive.getStatut()).isEqualTo(StatutAnneeScolaire.CLOTUREE);
        assertThat(ancienneActive.getDateCloture()).isNotNull();
    }

    @Test
    void doitActiverSansEffetSecondaire_quandAucuneAnneeActive() {
        UUID idCible = UUID.randomUUID();
        AnneeScolaire cible = anneeAvecId(idCible, "2026-2027",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.PREPARATION);
        when(repository.findById(idCible)).thenReturn(Optional.of(cible));
        when(repository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.empty());

        AnneeScolaire resultat = service.activer(idCible);

        assertThat(resultat.getStatut()).isEqualTo(StatutAnneeScolaire.ACTIVE);
    }

    // ------------------------------------------------------------------
    // R9 : dates modifiables seulement en PREPARATION / ACTIVE
    // ------------------------------------------------------------------

    @Test
    void doitRejeterModificationDesDates_quandAnneeCloturee() {
        UUID id = UUID.randomUUID();
        AnneeScolaire cloturee = anneeAvecId(id, "2025-2026", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 7, 15),
                StatutAnneeScolaire.CLOTUREE);
        when(repository.findById(id)).thenReturn(Optional.of(cloturee));

        ModifierAnneeScolaireRequestDto requete =
                new ModifierAnneeScolaireRequestDto(LocalDate.of(2025, 9, 15), LocalDate.of(2026, 7, 20), null);

        assertThatThrownBy(() -> service.modifier(id, requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_CLOTUREE_IMMUABLE);
    }

    @Test
    void doitAutoriserModificationDesDates_quandAnneeActive() {
        UUID id = UUID.randomUUID();
        AnneeScolaire active = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.ACTIVE);
        when(repository.findById(id)).thenReturn(Optional.of(active));

        ModifierAnneeScolaireRequestDto requete =
                new ModifierAnneeScolaireRequestDto(LocalDate.of(2026, 9, 15), LocalDate.of(2027, 7, 20), null);

        AnneeScolaire modifiee = service.modifier(id, requete);

        assertThat(modifiee.getDateDebut()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(modifiee.getDateFin()).isEqualTo(LocalDate.of(2027, 7, 20));
    }

    // ------------------------------------------------------------------
    // R10 : cloturer() exige ACTIVE, renseigne dateCloture
    // ------------------------------------------------------------------

    @Test
    void doitCloturer_quandAnneeActive() {
        UUID id = UUID.randomUUID();
        AnneeScolaire active = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.ACTIVE);
        when(repository.findById(id)).thenReturn(Optional.of(active));

        AnneeScolaire cloturee = service.cloturer(id);

        assertThat(cloturee.getStatut()).isEqualTo(StatutAnneeScolaire.CLOTUREE);
        assertThat(cloturee.getDateCloture()).isNotNull();
    }

    // ------------------------------------------------------------------
    // R12 (A4) : désactivation permise uniquement en PREPARATION
    // ------------------------------------------------------------------

    @Test
    void doitRejeterDesactivation_quandAnneeActive() {
        UUID id = UUID.randomUUID();
        AnneeScolaire active = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.ACTIVE);
        when(repository.findById(id)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.desactiver(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_DESACTIVATION_REFUSEE);
    }

    @Test
    void doitDesactiver_quandAnneeEnPreparation() {
        UUID id = UUID.randomUUID();
        AnneeScolaire preparation = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.PREPARATION);
        when(repository.findById(id)).thenReturn(Optional.of(preparation));

        service.desactiver(id);

        assertThat(preparation.isActif()).isFalse();
    }

    // ------------------------------------------------------------------
    // Cas limite transverse : ressource introuvable
    // ------------------------------------------------------------------

    @Test
    void doitRejeterObtenir_quandAnneeIntrouvable() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_INTROUVABLE);
    }

    @Test
    void doitRejeterObtenirActive_quandAucuneAnneeActive() {
        when(repository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenirActive())
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_ACTIVE_ABSENTE);
    }

    // ------------------------------------------------------------------
    // R14 : AnneeScolaireQuery, seul point d'entrée pour les autres modules
    // ------------------------------------------------------------------

    @Test
    void estModifiableDoitRenvoyerFaux_quandAnneeCloturee() {
        UUID id = UUID.randomUUID();
        AnneeScolaire cloturee = anneeAvecId(id, "2025-2026", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 7, 15),
                StatutAnneeScolaire.CLOTUREE);
        when(repository.findById(id)).thenReturn(Optional.of(cloturee));

        assertThat(service.estModifiable(id)).isFalse();
    }

    @Test
    void estModifiableDoitRenvoyerVrai_quandAnneeEnPreparationOuActive() {
        UUID id = UUID.randomUUID();
        AnneeScolaire preparation = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.PREPARATION);
        when(repository.findById(id)).thenReturn(Optional.of(preparation));

        assertThat(service.estModifiable(id)).isTrue();
    }

    @Test
    void estModifiableDoitRenvoyerFaux_quandAnneeIntrouvable() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.estModifiable(id)).isFalse();
    }

    @Test
    void idAnneeActiveDoitRenvoyerLidDeLanneeActive() {
        UUID id = UUID.randomUUID();
        AnneeScolaire active = anneeAvecId(id, "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15),
                StatutAnneeScolaire.ACTIVE);
        when(repository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThat(service.idAnneeActive()).contains(id);
    }

    @Test
    void idAnneeActiveDoitRenvoyerVide_quandAucuneAnneeActive() {
        when(repository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(service.idAnneeActive()).isEmpty();
    }
}
