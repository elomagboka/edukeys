package tg.novadigital.edukeys.academique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.PeriodeAcademique;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.domain.TypePeriode;
import tg.novadigital.edukeys.academique.repository.AnneeScolaireRepository;
import tg.novadigital.edukeys.academique.repository.PeriodeAcademiqueRepository;
import tg.novadigital.edukeys.academique.web.CreerPeriodeAcademiqueRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierPeriodeAcademiqueRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Tests unitaires du service {@link PeriodeAcademiqueService} : règles R1 à
 * R10 de la spec US-05. La contrainte d'exclusion SQL (R4) est vérifiée par
 * un test d'intégration séparé, pas ici.
 */
class PeriodeAcademiqueServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 15);

    private PeriodeAcademiqueRepository periodeAcademiqueRepository;
    private AnneeScolaireRepository anneeScolaireRepository;
    private EntityManager entityManager;
    private Clock clock;
    private PeriodeAcademiqueService service;
    private UUID etablissementId;
    private PorteeEtablissement portee;

    @BeforeEach
    void configurer() {
        periodeAcademiqueRepository = mock(PeriodeAcademiqueRepository.class);
        anneeScolaireRepository = mock(AnneeScolaireRepository.class);
        entityManager = mock(EntityManager.class);
        clock = Clock.fixed(AUJOURDHUI.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new PeriodeAcademiqueService(periodeAcademiqueRepository, anneeScolaireRepository, entityManager, clock);
        etablissementId = UUID.randomUUID();
        portee = ContexteEtablissement.ouvrir(etablissementId);

        when(periodeAcademiqueRepository.save(any(PeriodeAcademique.class))).thenAnswer(inv -> inv.getArgument(0));
        when(periodeAcademiqueRepository.existsByEtablissementIdAndAnneeScolaireIdAndLibelleAndActifTrue(any(), any(), anyString()))
                .thenReturn(false);
        when(periodeAcademiqueRepository.existsByEtablissementIdAndAnneeScolaireIdAndOrdreAndActifTrue(any(), any(), anyInt()))
                .thenReturn(false);
    }

    @AfterEach
    void fermerContexte() {
        portee.close();
    }

    private AnneeScolaire anneeAvecId(UUID id, LocalDate debut, LocalDate fin, StatutAnneeScolaire statut) {
        AnneeScolaire annee = new AnneeScolaire(etablissementId, "2026-2027", debut, fin);
        ReflectionTestUtils.setField(annee, "id", id);
        ReflectionTestUtils.setField(annee, "statut", statut);
        return annee;
    }

    private PeriodeAcademique periodeAvecId(UUID id, UUID anneeScolaireId, String libelle, int ordre,
                                             LocalDate debut, LocalDate fin) {
        PeriodeAcademique periode = new PeriodeAcademique(etablissementId, anneeScolaireId, libelle,
                TypePeriode.TRIMESTRE, ordre, debut, fin);
        ReflectionTestUtils.setField(periode, "id", id);
        return periode;
    }

    private CreerPeriodeAcademiqueRequestDto requeteValide(UUID anneeScolaireId, LocalDate debut, LocalDate fin, int ordre) {
        return new CreerPeriodeAcademiqueRequestDto(anneeScolaireId, "1er trimestre", TypePeriode.TRIMESTRE, ordre, debut, fin);
    }

    // ------------------------------------------------------------------
    // R1 : dateFin > dateDebut
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandDateFinAnterieureADateDebut() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_DATES_INCOHERENTES);
    }

    // ------------------------------------------------------------------
    // R2 : durée entre 21 et 250 jours
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandDureeInferieureAMinimum() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_DUREE_INVALIDE);
    }

    @Test
    void doitRejeterCreation_quandDureeSuperieureAMaximum() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(260), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_DUREE_INVALIDE);
    }

    @Test
    void doitAccepterCreation_quandDureeExactementAuxBornes() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto borneBasse =
                requeteValide(anneeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(21), 1);
        PeriodeAcademique creeeBasse = service.creer(borneBasse);
        assertThat(creeeBasse.getDateFin()).isEqualTo(LocalDate.of(2026, 1, 22));

        CreerPeriodeAcademiqueRequestDto borneHaute =
                requeteValide(anneeId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1).plusDays(250), 2);
        PeriodeAcademique creeeHaute = service.creer(borneHaute);
        assertThat(creeeHaute.getDateDebut()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    // ------------------------------------------------------------------
    // R3 : plage incluse dans les bornes de l'année (bornes comprises)
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandDateDebutAvantLanneeScolaire() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_HORS_BORNES_ANNEE);
    }

    @Test
    void doitRejeterCreation_quandDateFinApresLanneeScolaire() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2027, 6, 1), LocalDate.of(2027, 8, 1), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_HORS_BORNES_ANNEE);
    }

    @Test
    void doitAccepterCreation_quandDatesEgalesAuxBornesDeLannee() {
        UUID anneeId = UUID.randomUUID();
        LocalDate debutAnnee = LocalDate.of(2026, 9, 1);
        LocalDate finAnnee = debutAnnee.plusDays(200);
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, debutAnnee, finAnnee, StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto requete = requeteValide(anneeId, debutAnnee, finAnnee, 1);

        PeriodeAcademique creee = service.creer(requete);

        assertThat(creee.getDateDebut()).isEqualTo(debutAnnee);
        assertThat(creee.getDateFin()).isEqualTo(finAnnee);
    }

    // ------------------------------------------------------------------
    // R5 : libellé et ordre uniques parmi les périodes actives de l'année
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandLibelleDejaUtilise() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));
        when(periodeAcademiqueRepository.existsByEtablissementIdAndAnneeScolaireIdAndLibelleAndActifTrue(
                etablissementId, anneeId, "1er trimestre")).thenReturn(true);

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_LIBELLE_DUPLIQUE);
    }

    @Test
    void doitRejeterCreation_quandOrdreDejaUtilise() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));
        when(periodeAcademiqueRepository.existsByEtablissementIdAndAnneeScolaireIdAndOrdreAndActifTrue(etablissementId, anneeId, 1))
                .thenReturn(true);

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_ORDRE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R6 : coexistence de types différents sur une même année, sans contrôle
    // ------------------------------------------------------------------

    @Test
    void doitAccepterCreation_avecUnTypeDifferentDuneAutrePeriodeExistanteSurLaMemeAnnee() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));

        CreerPeriodeAcademiqueRequestDto requeteSemestre = new CreerPeriodeAcademiqueRequestDto(
                anneeId, "Semestre 1", TypePeriode.SEMESTRE, 1, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15));

        PeriodeAcademique creee = service.creer(requeteSemestre);

        assertThat(creee.getType()).isEqualTo(TypePeriode.SEMESTRE);
    }

    // ------------------------------------------------------------------
    // R7 : « en cours »
    // ------------------------------------------------------------------

    @Test
    void estEnCoursDoitRenvoyerVrai_quandDateDuJourDansLaPlageDeLAnneeActive() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.of(anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));
        PeriodeAcademique periode = periodeAvecId(UUID.randomUUID(), anneeId, "T2", 2,
                AUJOURDHUI.minusDays(5), AUJOURDHUI.plusDays(30));

        assertThat(service.estEnCours(periode)).isTrue();
    }

    @Test
    void estEnCoursDoitRenvoyerFaux_quandAnneeDeLaPeriodeNestPasLactive() {
        UUID anneeActiveId = UUID.randomUUID();
        UUID autreAnneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.of(anneeAvecId(anneeActiveId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));
        PeriodeAcademique periode = periodeAvecId(UUID.randomUUID(), autreAnneeId, "T2", 2,
                AUJOURDHUI.minusDays(5), AUJOURDHUI.plusDays(30));

        assertThat(service.estEnCours(periode)).isFalse();
    }

    @Test
    void estEnCoursDoitRenvoyerFaux_quandPeriodeInactive() {
        UUID anneeId = UUID.randomUUID();
        PeriodeAcademique periode = periodeAvecId(UUID.randomUUID(), anneeId, "T2", 2,
                AUJOURDHUI.minusDays(5), AUJOURDHUI.plusDays(30));
        periode.desactiver();

        assertThat(service.estEnCours(periode)).isFalse();
    }

    @Test
    void obtenirEnCoursDoitLeverRessourceIntrouvable_quandAucuneAnneeActive() {
        when(anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenirEnCours())
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_EN_COURS_ABSENTE);
    }

    // ------------------------------------------------------------------
    // R8 : refus si l'année est CLOTUREE
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandAnneeCloturee() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.CLOTUREE)));

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_ANNEE_CLOTUREE);
    }

    @Test
    void doitRejeterDesactivation_quandAnneeCloturee() {
        UUID anneeId = UUID.randomUUID();
        UUID periodeId = UUID.randomUUID();
        PeriodeAcademique periode = periodeAvecId(periodeId, anneeId, "T1", 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        when(periodeAcademiqueRepository.findById(periodeId)).thenReturn(Optional.of(periode));
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.CLOTUREE)));

        assertThatThrownBy(() -> service.desactiver(periodeId))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_ANNEE_CLOTUREE);
    }

    @Test
    void doitAutoriserCreation_quandAnneeEnPreparation() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.PREPARATION)));

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 1);

        PeriodeAcademique creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("1er trimestre");
    }

    // ------------------------------------------------------------------
    // R9 : type et anneeScolaireId immuables (aucun champ dans le DTO de modification)
    // ------------------------------------------------------------------

    @Test
    void modifierDoitConserverLeTypeEtLAnneeScolaire() {
        UUID anneeId = UUID.randomUUID();
        UUID periodeId = UUID.randomUUID();
        PeriodeAcademique periode = periodeAvecId(periodeId, anneeId, "T1", 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
        when(periodeAcademiqueRepository.findById(periodeId)).thenReturn(Optional.of(periode));
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(
                anneeAvecId(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15), StatutAnneeScolaire.ACTIVE)));

        ModifierPeriodeAcademiqueRequestDto requete =
                new ModifierPeriodeAcademiqueRequestDto("T1 modifié", 1, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5));

        PeriodeAcademique modifiee = service.modifier(periodeId, requete);

        assertThat(modifiee.getType()).isEqualTo(TypePeriode.TRIMESTRE);
        assertThat(modifiee.getAnneeScolaireId()).isEqualTo(anneeId);
        assertThat(modifiee.getLibelle()).isEqualTo("T1 modifié");
    }

    // ------------------------------------------------------------------
    // Cas limite transverse : ressource introuvable
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandAnneeScolaireIntrouvable() {
        UUID anneeId = UUID.randomUUID();
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.empty());

        CreerPeriodeAcademiqueRequestDto requete =
                requeteValide(anneeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_ANNEE_SCOLAIRE_INTROUVABLE);
    }

    @Test
    void doitRejeterObtenir_quandPeriodeIntrouvable() {
        UUID id = UUID.randomUUID();
        when(periodeAcademiqueRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.PERIODE_ACADEMIQUE_INTROUVABLE);
    }
}
