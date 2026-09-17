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
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.repository.AffectationMatiereRepository;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.academique.repository.CycleRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.CreerNiveauRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Tests unitaires du service {@link NiveauService} : R1 à R5 et R13 de la
 * spec US-02, en particulier le rattachement obligatoire à un {@link Cycle}
 * actif de l'établissement courant (R5).
 */
class NiveauServiceTest {

    private NiveauRepository niveauRepository;
    private CycleRepository cycleRepository;
    private ClasseRepository classeRepository;
    private AffectationMatiereRepository affectationMatiereRepository;
    private EntityManager entityManager;
    private NiveauService service;
    private UUID etablissementId;
    private PorteeEtablissement portee;

    @BeforeEach
    void configurer() {
        niveauRepository = mock(NiveauRepository.class);
        cycleRepository = mock(CycleRepository.class);
        classeRepository = mock(ClasseRepository.class);
        affectationMatiereRepository = mock(AffectationMatiereRepository.class);
        entityManager = mock(EntityManager.class);
        service = new NiveauService(niveauRepository, cycleRepository, classeRepository, affectationMatiereRepository, entityManager);
        etablissementId = UUID.randomUUID();
        portee = ContexteEtablissement.ouvrir(etablissementId);

        when(niveauRepository.save(any(Niveau.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void fermerContexte() {
        portee.close();
    }

    private Cycle cycleActif(UUID id) {
        Cycle cycle = new Cycle(etablissementId, "Collège", null, 1);
        ReflectionTestUtils.setField(cycle, "id", id);
        return cycle;
    }

    private Cycle cycleInactif(UUID id) {
        Cycle cycle = cycleActif(id);
        cycle.desactiver();
        return cycle;
    }

    /** Simule la {@code DataIntegrityViolationException} levée au flush lors d'une course de concurrence. */
    private static DataIntegrityViolationException violationSur(String nomContrainte) {
        return new DataIntegrityViolationException("violation",
                new org.hibernate.exception.ConstraintViolationException("violation", null, nomContrainte));
    }

    // ------------------------------------------------------------------
    // R1 : trim / code vide -> null
    // ------------------------------------------------------------------

    @Test
    void doitTrimmerLeLibelle_alaCreation() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("  6ème  ", null, 1, cycleId);

        Niveau cree = service.creer(requete);

        assertThat(cree.getLibelle()).isEqualTo("6ème");
    }

    // ------------------------------------------------------------------
    // R2 : rang positif, unique par établissement
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandRangDejaUtilise() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        when(niveauRepository.existsByEtablissementIdAndRangAndActifTrue(etablissementId, 1)).thenReturn(true);
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", null, 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_RANG_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R3 : libellé unique
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandLibelleDejaUtilise() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        when(niveauRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, "6ème")).thenReturn(true);
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", null, 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_LIBELLE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R4 : code unique s'il est renseigné
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandCodeDejaUtilise() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        when(niveauRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, "6E")).thenReturn(true);
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", "6e", 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_CODE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R5 : cycle obligatoire, doit exister et être actif
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandCycleIntrouvable() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.empty());
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", null, 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_INTROUVABLE);
    }

    @Test
    void doitRejeterCreation_quandCycleInactif() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleInactif(cycleId)));
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", null, 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_INTROUVABLE);
    }

    // ------------------------------------------------------------------
    // R13 : refus de désactivation en cascade (classes actives)
    // ------------------------------------------------------------------

    @Test
    void doitRejeterDesactivation_quandNiveauPorteEncoreDesClassesActives() {
        UUID id = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        Niveau niveau = new Niveau(etablissementId, "6ème", null, 1, cycleActif(cycleId));
        ReflectionTestUtils.setField(niveau, "id", id);
        when(niveauRepository.findById(id)).thenReturn(Optional.of(niveau));
        when(classeRepository.countByEtablissementIdAndNiveauIdAndActifTrue(etablissementId, id)).thenReturn(2L);

        assertThatThrownBy(() -> service.desactiver(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_NON_DESACTIVABLE);
    }

    @Test
    void doitDesactiver_quandNiveauNePorteAucuneClasseActive() {
        UUID id = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        Niveau niveau = new Niveau(etablissementId, "6ème", null, 1, cycleActif(cycleId));
        ReflectionTestUtils.setField(niveau, "id", id);
        when(niveauRepository.findById(id)).thenReturn(Optional.of(niveau));
        when(classeRepository.countByEtablissementIdAndNiveauIdAndActifTrue(etablissementId, id)).thenReturn(0L);
        when(affectationMatiereRepository.countByEtablissementIdAndNiveauIdAndActifTrue(etablissementId, id)).thenReturn(0L);

        service.desactiver(id);

        assertThat(niveau.isActif()).isFalse();
    }

    // ------------------------------------------------------------------
    // R9 (US-03) : refus de désactivation quand une affectation de matière
    // active référence encore ce niveau.
    // ------------------------------------------------------------------

    @Test
    void doitRejeterDesactivation_quandNiveauEncoreReferenceParUneAffectationDeMatiereActive() {
        UUID id = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        Niveau niveau = new Niveau(etablissementId, "6ème", null, 1, cycleActif(cycleId));
        ReflectionTestUtils.setField(niveau, "id", id);
        when(niveauRepository.findById(id)).thenReturn(Optional.of(niveau));
        when(classeRepository.countByEtablissementIdAndNiveauIdAndActifTrue(etablissementId, id)).thenReturn(0L);
        when(affectationMatiereRepository.countByEtablissementIdAndNiveauIdAndActifTrue(etablissementId, id)).thenReturn(1L);

        assertThatThrownBy(() -> service.desactiver(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_NON_DESACTIVABLE);
    }

    // ------------------------------------------------------------------
    // Cas limite transverse : ressource introuvable
    // ------------------------------------------------------------------

    @Test
    void doitRejeterObtenir_quandNiveauIntrouvable() {
        UUID id = UUID.randomUUID();
        when(niveauRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_INTROUVABLE);
    }

    // ------------------------------------------------------------------
    // Point IMPORTANT n°3 (revue US-02) : discrimination de la contrainte
    // violée en concurrence réelle.
    // ------------------------------------------------------------------

    @Test
    void doitTraduireEnRangDuplique_quandLaContrainteRangEstViolee() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        when(niveauRepository.save(any(Niveau.class))).thenThrow(violationSur("uk_niveaux_rang_actif"));
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", null, 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_RANG_DUPLIQUE);
    }

    @Test
    void doitTraduireEnCodeDuplique_quandLaContrainteCodeEstViolee() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        when(niveauRepository.save(any(Niveau.class))).thenThrow(violationSur("uk_niveaux_code_actif"));
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", "6e", 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_CODE_DUPLIQUE);
    }

    @Test
    void doitTraduireEnLibelleDuplique_quandLaContrainteLibelleEstViolee() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        when(niveauRepository.save(any(Niveau.class))).thenThrow(violationSur("uk_niveaux_libelle_actif"));
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", null, 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_LIBELLE_DUPLIQUE);
    }

    @Test
    void neDoitPasDeviner_quandLaContrainteVioleeEstInconnue() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycleActif(cycleId)));
        DataIntegrityViolationException violation = violationSur("uk_inconnue");
        when(niveauRepository.save(any(Niveau.class))).thenThrow(violation);
        CreerNiveauRequestDto requete = new CreerNiveauRequestDto("6ème", null, 1, cycleId);

        assertThatThrownBy(() -> service.creer(requete)).isSameAs(violation);
    }
}
