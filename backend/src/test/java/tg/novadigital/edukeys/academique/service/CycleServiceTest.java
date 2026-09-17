package tg.novadigital.edukeys.academique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.repository.CycleRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.CreerCycleRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierCycleRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Tests unitaires du service {@link CycleService} : règles R1 à R4 et R13 de
 * la spec US-02 (module {@code academique}, hiérarchie D1 : Cycle {@literal >}
 * Niveau {@literal >} Classe).
 */
class CycleServiceTest {

    private CycleRepository cycleRepository;
    private NiveauRepository niveauRepository;
    private EntityManager entityManager;
    private CycleService service;
    private UUID etablissementId;
    private PorteeEtablissement portee;

    @BeforeEach
    void configurer() {
        cycleRepository = mock(CycleRepository.class);
        niveauRepository = mock(NiveauRepository.class);
        entityManager = mock(EntityManager.class);
        service = new CycleService(cycleRepository, niveauRepository, entityManager);
        etablissementId = UUID.randomUUID();
        portee = ContexteEtablissement.ouvrir(etablissementId);

        when(cycleRepository.save(any(Cycle.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void fermerContexte() {
        portee.close();
    }

    private Cycle cycleAvecId(UUID id, String libelle, String code, int rang) {
        Cycle cycle = new Cycle(etablissementId, libelle, code, rang);
        ReflectionTestUtils.setField(cycle, "id", id);
        return cycle;
    }

    /** Simule la {@code DataIntegrityViolationException} levée au flush lors d'une course de concurrence. */
    private static DataIntegrityViolationException violationSur(String nomContrainte) {
        return new DataIntegrityViolationException("violation",
                new org.hibernate.exception.ConstraintViolationException("violation", null, nomContrainte));
    }

    // ------------------------------------------------------------------
    // R1 : trim() du libellé, code vide normalisé en null
    // ------------------------------------------------------------------

    @Test
    void doitTrimmerLeLibelle_alaCreation() {
        CreerCycleRequestDto requete = new CreerCycleRequestDto("  Collège  ", null, 1);

        Cycle cree = service.creer(requete);

        assertThat(cree.getLibelle()).isEqualTo("Collège");
    }

    @Test
    void doitNormaliserCodeVideEnNull_alaCreation() {
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", "   ", 1);

        Cycle cree = service.creer(requete);

        assertThat(cree.getCode()).isNull();
    }

    @Test
    void doitMettreLeCodeEnMajuscules_alaCreation() {
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", "col", 1);

        Cycle cree = service.creer(requete);

        assertThat(cree.getCode()).isEqualTo("COL");
    }

    // ------------------------------------------------------------------
    // R2 : rang strictement positif et unique par établissement (actifs)
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandRangDejaUtiliseParUnCycleActif() {
        when(cycleRepository.existsByEtablissementIdAndRangAndActifTrue(etablissementId, 1)).thenReturn(true);
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Lycée", null, 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_RANG_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R3 : libellé unique par établissement parmi les actifs
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandLibelleDejaUtiliseParUnCycleActif() {
        when(cycleRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, "Collège")).thenReturn(true);
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", null, 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_LIBELLE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // R4 : code unique par établissement parmi les actifs, s'il est renseigné
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandCodeDejaUtiliseParUnCycleActif() {
        when(cycleRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, "COL")).thenReturn(true);
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", "col", 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_CODE_DUPLIQUE);
    }

    @Test
    void doitAccepterCreation_quandCodeAbsent() {
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", null, 1);

        Cycle cree = service.creer(requete);

        assertThat(cree.getCode()).isNull();
    }

    // ------------------------------------------------------------------
    // R13 : désactivation en cascade interdite (niveaux actifs)
    // ------------------------------------------------------------------

    @Test
    void doitRejeterDesactivation_quandCyclePorteEncoreDesNiveauxActifs() {
        UUID id = UUID.randomUUID();
        Cycle cycle = cycleAvecId(id, "Collège", null, 1);
        when(cycleRepository.findById(id)).thenReturn(java.util.Optional.of(cycle));
        when(niveauRepository.countByEtablissementIdAndCycleIdAndActifTrue(etablissementId, id)).thenReturn(1L);

        assertThatThrownBy(() -> service.desactiver(id))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_NON_DESACTIVABLE);
    }

    @Test
    void doitDesactiver_quandCycleNePorteAucunNiveauActif() {
        UUID id = UUID.randomUUID();
        Cycle cycle = cycleAvecId(id, "Collège", null, 1);
        when(cycleRepository.findById(id)).thenReturn(java.util.Optional.of(cycle));
        when(niveauRepository.countByEtablissementIdAndCycleIdAndActifTrue(etablissementId, id)).thenReturn(0L);

        service.desactiver(id);

        assertThat(cycle.isActif()).isFalse();
    }

    // ------------------------------------------------------------------
    // Cas limite transverse : ressource introuvable
    // ------------------------------------------------------------------

    @Test
    void doitRejeterObtenir_quandCycleIntrouvable() {
        UUID id = UUID.randomUUID();
        when(cycleRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_INTROUVABLE);
    }

    @Test
    void doitConserverLeRangEtLeCode_quandModificationSansChangement() {
        UUID id = UUID.randomUUID();
        Cycle existant = cycleAvecId(id, "Collège", "COL", 1);
        when(cycleRepository.findById(id)).thenReturn(java.util.Optional.of(existant));

        ModifierCycleRequestDto requete = new ModifierCycleRequestDto("Collège", "col", 1);

        Cycle modifie = service.modifier(id, requete);

        assertThat(modifie.getLibelle()).isEqualTo("Collège");
        assertThat(modifie.getCode()).isEqualTo("COL");
        assertThat(modifie.getRang()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Point IMPORTANT n°3 (revue US-02) : discrimination de la contrainte
    // violée en concurrence réelle (la vérification applicative ne l'a pas
    // vue, seul le filet base de données tranche).
    // ------------------------------------------------------------------

    @Test
    void doitTraduireEnRangDuplique_quandLaContrainteRangEstViolee() {
        when(cycleRepository.save(any(Cycle.class))).thenThrow(violationSur("uk_cycles_rang_actif"));
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", null, 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_RANG_DUPLIQUE);
    }

    @Test
    void doitTraduireEnCodeDuplique_quandLaContrainteCodeEstViolee() {
        when(cycleRepository.save(any(Cycle.class))).thenThrow(violationSur("uk_cycles_code_actif"));
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", "col", 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_CODE_DUPLIQUE);
    }

    @Test
    void doitTraduireEnLibelleDuplique_quandLaContrainteLibelleEstViolee() {
        when(cycleRepository.save(any(Cycle.class))).thenThrow(violationSur("uk_cycles_libelle_actif"));
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", null, 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.CYCLE_LIBELLE_DUPLIQUE);
    }

    @Test
    void neDoitPasDeviner_quandLaContrainteVioleeEstInconnue() {
        DataIntegrityViolationException violation = violationSur("uk_inconnue");
        when(cycleRepository.save(any(Cycle.class))).thenThrow(violation);
        CreerCycleRequestDto requete = new CreerCycleRequestDto("Collège", null, 1);

        assertThatThrownBy(() -> service.creer(requete))
                .isSameAs(violation);
    }
}
