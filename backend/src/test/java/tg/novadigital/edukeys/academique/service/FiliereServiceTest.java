package tg.novadigital.edukeys.academique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.repository.AffectationMatiereRepository;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.academique.repository.CycleRepository;
import tg.novadigital.edukeys.academique.repository.FiliereRepository;
import tg.novadigital.edukeys.academique.web.CreerFiliereRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Tests unitaires du service {@link FiliereService} : R1, R3, R4, R6 (cycle
 * optionnel, D4) et R13 de la spec US-02.
 */
class FiliereServiceTest {

    private FiliereRepository filiereRepository;
    private CycleRepository cycleRepository;
    private ClasseRepository classeRepository;
    private AffectationMatiereRepository affectationMatiereRepository;
    private EntityManager entityManager;
    private FiliereService service;
    private UUID etablissementId;
    private PorteeEtablissement portee;

    @BeforeEach
    void configurer() {
        filiereRepository = mock(FiliereRepository.class);
        cycleRepository = mock(CycleRepository.class);
        classeRepository = mock(ClasseRepository.class);
        affectationMatiereRepository = mock(AffectationMatiereRepository.class);
        entityManager = mock(EntityManager.class);
        service = new FiliereService(filiereRepository, cycleRepository, classeRepository, affectationMatiereRepository, entityManager);
        etablissementId = UUID.randomUUID();
        portee = ContexteEtablissement.ouvrir(etablissementId);

        when(filiereRepository.save(any(Filiere.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void fermerContexte() {
        portee.close();
    }

    private Cycle cycleActif(UUID id) {
        Cycle cycle = new Cycle(etablissementId, "Lycée", null, 2);
        ReflectionTestUtils.setField(cycle, "id", id);
        return cycle;
    }

    /** Simule la {@code DataIntegrityViolationException} levée au flush lors d'une course de concurrence. */
    private static DataIntegrityViolationException violationSur(String nomContrainte) {
        return new DataIntegrityViolationException("violation",
                new org.hibernate.exception.ConstraintViolationException("violation", null, nomContrainte));
    }

    // ------------------------------------------------------------------
    // D4 / R6 : le rattachement au cycle est optionnel
    // ------------------------------------------------------------------

    @Test
    void doitAccepterCreation_sansCycle() {
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", "D", null);

        Filiere creee = service.creer(requete);

        assertThat(creee.getCycle()).isNull();
    }

    @Test
    void doitRattacherAuCycle_quandCycleFourniEtActif() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", "D", cycleId);

        Filiere creee = service.creer(requete);

        assertThat(creee.getCycle().getId()).isEqualTo(cycleId);
    }

    @Test
    void doitRejeterCreation_quandCycleFourniIntrouvable() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.empty());
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", "D", cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_INTROUVABLE);
    }

    // ------------------------------------------------------------------
    // R1 : trim / code vide -> null
    // ------------------------------------------------------------------

    @Test
    void doitTrimmerLeLibelle_alaCreation() {
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("  Scientifique  ", null, null);

        Filiere creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("Scientifique");
        assertThat(creee.getCode()).isNull();
    }

    // ------------------------------------------------------------------
    // R3 : libellé unique par établissement parmi les actives
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandLibelleDejaUtilise() {
        when(filiereRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, "Scientifique")).thenReturn(true);
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", null, null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_LIBELLE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R4 : code unique s'il est renseigné
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandCodeDejaUtilise() {
        when(filiereRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, "D")).thenReturn(true);
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", "d", null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_CODE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R13 : refus de désactivation en cascade (classes actives)
    // ------------------------------------------------------------------

    @Test
    void doitRejeterDesactivation_quandFiliereEncoreReferenceeParDesClassesActives() {
        UUID id = UUID.randomUUID();
        Filiere filiere = new Filiere(etablissementId, "Scientifique", "D", null);
        ReflectionTestUtils.setField(filiere, "id", id);
        when(filiereRepository.findById(id)).thenReturn(Optional.of(filiere));
        when(classeRepository.countByEtablissementIdAndFiliereIdAndActifTrue(etablissementId, id)).thenReturn(3L);

        assertThatThrownBy(() -> service.desactiver(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_NON_DESACTIVABLE);
    }

    @Test
    void doitDesactiver_quandFiliereNonReferencee() {
        UUID id = UUID.randomUUID();
        Filiere filiere = new Filiere(etablissementId, "Scientifique", "D", null);
        ReflectionTestUtils.setField(filiere, "id", id);
        when(filiereRepository.findById(id)).thenReturn(Optional.of(filiere));
        when(classeRepository.countByEtablissementIdAndFiliereIdAndActifTrue(etablissementId, id)).thenReturn(0L);
        when(affectationMatiereRepository.countByEtablissementIdAndFiliereIdAndActifTrue(etablissementId, id)).thenReturn(0L);

        service.desactiver(id);

        assertThat(filiere.isActif()).isFalse();
    }

    // ------------------------------------------------------------------
    // R9 (US-03) : refus de désactivation quand une affectation de matière
    // active référence encore cette filière.
    // ------------------------------------------------------------------

    @Test
    void doitRejeterDesactivation_quandFiliereEncoreReferenceeParUneAffectationDeMatiereActive() {
        UUID id = UUID.randomUUID();
        Filiere filiere = new Filiere(etablissementId, "Scientifique", "D", null);
        ReflectionTestUtils.setField(filiere, "id", id);
        when(filiereRepository.findById(id)).thenReturn(Optional.of(filiere));
        when(classeRepository.countByEtablissementIdAndFiliereIdAndActifTrue(etablissementId, id)).thenReturn(0L);
        when(affectationMatiereRepository.countByEtablissementIdAndFiliereIdAndActifTrue(etablissementId, id)).thenReturn(1L);

        assertThatThrownBy(() -> service.desactiver(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_NON_DESACTIVABLE);
    }

    // ------------------------------------------------------------------
    // Cas limite transverse : ressource introuvable
    // ------------------------------------------------------------------

    @Test
    void doitRejeterObtenir_quandFiliereIntrouvable() {
        UUID id = UUID.randomUUID();
        when(filiereRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_INTROUVABLE);
    }

    // ------------------------------------------------------------------
    // Point IMPORTANT n°3 (revue US-02) : discrimination de la contrainte
    // violée en concurrence réelle.
    // ------------------------------------------------------------------

    @Test
    void doitTraduireEnCodeDuplique_quandLaContrainteCodeEstViolee() {
        when(filiereRepository.save(any(Filiere.class))).thenThrow(violationSur("uk_filieres_code_actif"));
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", "d", null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_CODE_DUPLIQUE);
    }

    @Test
    void doitTraduireEnLibelleDuplique_quandLaContrainteLibelleEstViolee() {
        when(filiereRepository.save(any(Filiere.class))).thenThrow(violationSur("uk_filieres_libelle_actif"));
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", null, null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_LIBELLE_DUPLIQUE);
    }

    @Test
    void neDoitPasDeviner_quandLaContrainteVioleeEstInconnue() {
        DataIntegrityViolationException violation = violationSur("uk_inconnue");
        when(filiereRepository.save(any(Filiere.class))).thenThrow(violation);
        CreerFiliereRequestDto requete = new CreerFiliereRequestDto("Scientifique", null, null);

        assertThatThrownBy(() -> service.creer(requete)).isSameAs(violation);
    }
}
