package tg.novadigital.edukeys.academique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AffectationMatiere;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.domain.Matiere;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.repository.AffectationMatiereRepository;
import tg.novadigital.edukeys.academique.repository.FiliereRepository;
import tg.novadigital.edukeys.academique.repository.MatiereRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.AffectationMatiereRequestDto;
import tg.novadigital.edukeys.academique.web.CreerMatiereRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Tests unitaires du service {@link MatiereService} (US-03) : création
 * indépendante des affectations, diff appliqué par {@code definirAffectations}
 * (R3 à R8) et désactivation en cascade des affectations actives (R8).
 */
class MatiereServiceTest {

    private MatiereRepository matiereRepository;
    private AffectationMatiereRepository affectationMatiereRepository;
    private NiveauRepository niveauRepository;
    private FiliereRepository filiereRepository;
    private EntityManager entityManager;
    private MatiereService service;
    private UUID etablissementId;
    private PorteeEtablissement portee;

    @BeforeEach
    void configurer() {
        matiereRepository = mock(MatiereRepository.class);
        affectationMatiereRepository = mock(AffectationMatiereRepository.class);
        niveauRepository = mock(NiveauRepository.class);
        filiereRepository = mock(FiliereRepository.class);
        entityManager = mock(EntityManager.class);
        service = new MatiereService(matiereRepository, affectationMatiereRepository, niveauRepository, filiereRepository, entityManager);
        etablissementId = UUID.randomUUID();
        portee = ContexteEtablissement.ouvrir(etablissementId);

        when(matiereRepository.save(any(Matiere.class))).thenAnswer(inv -> inv.getArgument(0));
        when(affectationMatiereRepository.save(any(AffectationMatiere.class))).thenAnswer(inv -> inv.getArgument(0));
        when(affectationMatiereRepository.findByEtablissementIdAndMatiereIdAndActifTrue(any(), any()))
                .thenReturn(List.of());
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

    private Niveau niveauActif(UUID id, Cycle cycle) {
        Niveau niveau = new Niveau(etablissementId, "Terminale D", null, 1, cycle);
        ReflectionTestUtils.setField(niveau, "id", id);
        return niveau;
    }

    private Niveau niveauInactif(UUID id, Cycle cycle) {
        Niveau niveau = niveauActif(id, cycle);
        niveau.desactiver();
        return niveau;
    }

    private Filiere filiereActive(UUID id, Cycle cycle) {
        Filiere filiere = new Filiere(etablissementId, "Scientifique", "D", cycle);
        ReflectionTestUtils.setField(filiere, "id", id);
        return filiere;
    }

    private Filiere filiereInactive(UUID id, Cycle cycle) {
        Filiere filiere = filiereActive(id, cycle);
        filiere.desactiver();
        return filiere;
    }

    private Matiere matiereActive(UUID id) {
        Matiere matiere = new Matiere(etablissementId, "Mathématiques", "MATH");
        ReflectionTestUtils.setField(matiere, "id", id);
        return matiere;
    }

    private Matiere matiereInactive(UUID id) {
        Matiere matiere = matiereActive(id);
        matiere.desactiver();
        return matiere;
    }

    /** Simule la {@code DataIntegrityViolationException} levée au flush lors d'une course de concurrence. */
    private static DataIntegrityViolationException violationSur(String nomContrainte) {
        return new DataIntegrityViolationException("violation",
                new org.hibernate.exception.ConstraintViolationException("violation", null, nomContrainte));
    }

    // ------------------------------------------------------------------
    // Critère d'acceptation : une matière existe indépendamment de ses affectations
    // ------------------------------------------------------------------

    @Test
    void doitCreerMatiere_sansAffectation() {
        CreerMatiereRequestDto requete = new CreerMatiereRequestDto("Mathématiques", "MATH", null);

        Matiere creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("Mathématiques");
        assertThat(creee.getCode()).isEqualTo("MATH");
        verify(affectationMatiereRepository, never()).save(any());
    }

    @Test
    void doitCreerMatiere_avecAffectationsInitiales() {
        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle)));

        CreerMatiereRequestDto requete = new CreerMatiereRequestDto("Mathématiques", "MATH",
                List.of(new AffectationMatiereRequestDto(niveauId, null, new BigDecimal("4"), new BigDecimal("5"), true)));

        Matiere creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("Mathématiques");
        verify(affectationMatiereRepository, times(1)).save(any(AffectationMatiere.class));
    }

    // ------------------------------------------------------------------
    // Libellé / code dupliqué -> 409
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandLibelleDejaUtilise() {
        when(matiereRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, "Mathématiques")).thenReturn(true);
        CreerMatiereRequestDto requete = new CreerMatiereRequestDto("Mathématiques", null, null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_LIBELLE_DUPLIQUE);
    }

    @Test
    void doitRejeterCreation_quandCodeDejaUtilise() {
        when(matiereRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, "MATH")).thenReturn(true);
        CreerMatiereRequestDto requete = new CreerMatiereRequestDto("Mathématiques", "math", null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_CODE_DUPLIQUE);
    }

    @Test
    void doitTraduireEnLibelleDuplique_quandLaContrainteLibelleEstVioleeEnConcurrence() {
        when(matiereRepository.save(any(Matiere.class))).thenThrow(violationSur("uk_matieres_libelle_actif"));
        CreerMatiereRequestDto requete = new CreerMatiereRequestDto("Mathématiques", null, null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_LIBELLE_DUPLIQUE);
    }

    @Test
    void doitTraduireEnCodeDuplique_quandLaContrainteCodeEstVioleeEnConcurrence() {
        when(matiereRepository.save(any(Matiere.class))).thenThrow(violationSur("uk_matieres_code_actif"));
        CreerMatiereRequestDto requete = new CreerMatiereRequestDto("Mathématiques", "MATH", null);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_CODE_DUPLIQUE);
    }

    @Test
    void neDoitPasDeviner_quandLaContrainteVioleeEstInconnue() {
        DataIntegrityViolationException violation = violationSur("uk_inconnue");
        when(matiereRepository.save(any(Matiere.class))).thenThrow(violation);
        CreerMatiereRequestDto requete = new CreerMatiereRequestDto("Mathématiques", null, null);

        assertThatThrownBy(() -> service.creer(requete)).isSameAs(violation);
    }

    // ------------------------------------------------------------------
    // definirAffectations : ajout, retrait (désactivation), mise à jour en place, idempotence
    // ------------------------------------------------------------------

    @Test
    void definirAffectations_doitAjouterUneNouvelleAffectation() {
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = matiereActive(matiereId);
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiere));

        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle)));

        service.definirAffectations(matiereId,
                List.of(new AffectationMatiereRequestDto(niveauId, null, new BigDecimal("4"), new BigDecimal("5"), true)));

        verify(affectationMatiereRepository, times(1)).save(any(AffectationMatiere.class));
    }

    @Test
    void definirAffectations_doitDesactiverUneAffectationAbsenteDeLaCible() {
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = matiereActive(matiereId);
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiere));

        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        Niveau niveau = niveauActif(niveauId, cycle);
        AffectationMatiere existante = new AffectationMatiere(etablissementId, matiere, niveau, null,
                new BigDecimal("4"), null, true);
        when(affectationMatiereRepository.findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, matiereId))
                .thenReturn(List.of(existante));

        service.definirAffectations(matiereId, List.of());

        assertThat(existante.isActif()).isFalse();
        verify(affectationMatiereRepository, times(1)).save(existante);
    }

    @Test
    void definirAffectations_listeVide_doitToutDesactiver() {
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = matiereActive(matiereId);
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiere));

        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        Niveau niveau1 = niveauActif(UUID.randomUUID(), cycle);
        Niveau niveau2 = niveauActif(UUID.randomUUID(), cycle);
        AffectationMatiere a1 = new AffectationMatiere(etablissementId, matiere, niveau1, null, null, null, null);
        AffectationMatiere a2 = new AffectationMatiere(etablissementId, matiere, niveau2, null, null, null, null);
        when(affectationMatiereRepository.findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, matiereId))
                .thenReturn(List.of(a1, a2));

        service.definirAffectations(matiereId, List.of());

        assertThat(a1.isActif()).isFalse();
        assertThat(a2.isActif()).isFalse();
    }

    @Test
    void definirAffectations_doitMettreAJourLesAttributsSansCreerNouvelleLigne() {
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = matiereActive(matiereId);
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiere));

        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        Niveau niveau = niveauActif(niveauId, cycle);
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveau));
        AffectationMatiere existante = new AffectationMatiere(etablissementId, matiere, niveau, null,
                new BigDecimal("4"), new BigDecimal("5"), true);
        when(affectationMatiereRepository.findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, matiereId))
                .thenReturn(List.of(existante));

        service.definirAffectations(matiereId,
                List.of(new AffectationMatiereRequestDto(niveauId, null, new BigDecimal("6"), new BigDecimal("5"), false)));

        assertThat(existante.getCoefficient()).isEqualByComparingTo("6");
        assertThat(existante.getObligatoire()).isFalse();
        verify(affectationMatiereRepository, never()).save(org.mockito.ArgumentMatchers.argThat(a -> a != existante));
        verify(affectationMatiereRepository, times(1)).save(existante);
    }

    @Test
    void definirAffectations_estIdempotent_quandLaCibleEstIdentiqueAlExistant() {
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = matiereActive(matiereId);
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiere));

        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        Niveau niveau = niveauActif(niveauId, cycle);
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveau));
        AffectationMatiere existante = new AffectationMatiere(etablissementId, matiere, niveau, null,
                new BigDecimal("4.00"), new BigDecimal("5.0"), true);
        when(affectationMatiereRepository.findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, matiereId))
                .thenReturn(List.of(existante));

        service.definirAffectations(matiereId,
                List.of(new AffectationMatiereRequestDto(niveauId, null, new BigDecimal("4"), new BigDecimal("5"), true)));

        verify(affectationMatiereRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Validation des requêtes d'affectation
    // ------------------------------------------------------------------

    @Test
    void definirAffectations_doitRejeter_quandCleDupliqueeDansLaRequete() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereActive(matiereId)));
        UUID niveauId = UUID.randomUUID();

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, null, new BigDecimal("4"), null, true),
                new AffectationMatiereRequestDto(niveauId, null, new BigDecimal("5"), null, true));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_AFFECTATION_CLE_DUPLIQUEE);
    }

    @Test
    void definirAffectations_doitRejeter_quandMelangeNiveauSeulEtNiveauFiliereSurLeMemeNiveau() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereActive(matiereId)));
        UUID niveauId = UUID.randomUUID();
        UUID filiereId = UUID.randomUUID();

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, null, null, null, null),
                new AffectationMatiereRequestDto(niveauId, filiereId, null, null, null));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_AFFECTATION_INCOHERENTE);
    }

    @Test
    void definirAffectations_doitRejeter_quandFiliereAppartientAUnAutreCycle() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereActive(matiereId)));

        UUID cycleNiveauId = UUID.randomUUID();
        Cycle cycleNiveau = cycleActif(cycleNiveauId);
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycleNiveau)));

        UUID cycleAutreId = UUID.randomUUID();
        Cycle cycleAutre = cycleActif(cycleAutreId);
        UUID filiereId = UUID.randomUUID();
        when(filiereRepository.findById(filiereId)).thenReturn(Optional.of(filiereActive(filiereId, cycleAutre)));

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, filiereId, null, null, null));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_CYCLE_INCOHERENT);
    }

    @Test
    void definirAffectations_doitRejeter_quandNiveauIntrouvable() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereActive(matiereId)));
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.empty());

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, null, null, null, null));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_INTROUVABLE);
    }

    @Test
    void definirAffectations_doitRejeter_quandNiveauInactif() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereActive(matiereId)));
        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauInactif(niveauId, cycle)));

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, null, null, null, null));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_INTROUVABLE);
    }

    @Test
    void definirAffectations_doitRejeter_quandFiliereIntrouvable() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereActive(matiereId)));
        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle)));
        UUID filiereId = UUID.randomUUID();
        when(filiereRepository.findById(filiereId)).thenReturn(Optional.empty());

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, filiereId, null, null, null));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_INTROUVABLE);
    }

    @Test
    void definirAffectations_doitRejeter_quandFiliereInactive() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereActive(matiereId)));
        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle)));
        UUID filiereId = UUID.randomUUID();
        when(filiereRepository.findById(filiereId)).thenReturn(Optional.of(filiereInactive(filiereId, cycle)));

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, filiereId, null, null, null));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_INTROUVABLE);
    }

    @Test
    void definirAffectations_doitRejeter_quandMatiereInactive() {
        UUID matiereId = UUID.randomUUID();
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiereInactive(matiereId)));
        UUID niveauId = UUID.randomUUID();

        List<AffectationMatiereRequestDto> affectations = List.of(
                new AffectationMatiereRequestDto(niveauId, null, null, null, null));

        assertThatThrownBy(() -> service.definirAffectations(matiereId, affectations))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_INACTIVE);
    }

    // ------------------------------------------------------------------
    // R8 : désactivation en cascade
    // ------------------------------------------------------------------

    @Test
    void desactiver_doitDesactiverLaMatiereEtSesAffectationsActives() {
        UUID matiereId = UUID.randomUUID();
        Matiere matiere = matiereActive(matiereId);
        when(matiereRepository.findById(matiereId)).thenReturn(Optional.of(matiere));

        UUID cycleId = UUID.randomUUID();
        Cycle cycle = cycleActif(cycleId);
        Niveau niveau = niveauActif(UUID.randomUUID(), cycle);
        AffectationMatiere active = new AffectationMatiere(etablissementId, matiere, niveau, null, null, null, null);
        when(affectationMatiereRepository.findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, matiereId))
                .thenReturn(List.of(active));

        service.desactiver(matiereId);

        assertThat(matiere.isActif()).isFalse();
        assertThat(active.isActif()).isFalse();
        verify(affectationMatiereRepository, times(1)).save(active);
    }

    // ------------------------------------------------------------------
    // Cas limite transverse : ressource introuvable
    // ------------------------------------------------------------------

    @Test
    void doitRejeterObtenir_quandMatiereIntrouvable() {
        UUID id = UUID.randomUUID();
        when(matiereRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.MATIERE_INTROUVABLE);
    }
}
