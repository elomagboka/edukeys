package tg.novadigital.edukeys.academique.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.repository.AnneeScolaireRepository;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.academique.repository.FiliereRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.CreerClasseRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierClasseRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.etablissement.SiteQuery;

/**
 * Tests unitaires du service {@link ClasseService} : règles R7 à R12 et D5 de
 * la spec US-02. Couvre en particulier R11 (isolation {@code site_id}, point
 * le plus sensible de l'US selon les arbitrages du tech lead) au niveau
 * service ; le test d'intégration {@code StructureAcademiqueIntegrationTest}
 * l'exerce de bout en bout via HTTP.
 */
class ClasseServiceTest {

    private ClasseRepository classeRepository;
    private NiveauRepository niveauRepository;
    private FiliereRepository filiereRepository;
    private AnneeScolaireRepository anneeScolaireRepository;
    private SiteQuery siteQuery;
    private EntityManager entityManager;
    private ClasseService service;
    private UUID etablissementId;
    private UUID sitePrincipalId;
    private PorteeEtablissement portee;

    @BeforeEach
    void configurer() {
        classeRepository = mock(ClasseRepository.class);
        niveauRepository = mock(NiveauRepository.class);
        filiereRepository = mock(FiliereRepository.class);
        anneeScolaireRepository = mock(AnneeScolaireRepository.class);
        siteQuery = mock(SiteQuery.class);
        entityManager = mock(EntityManager.class);
        service = new ClasseService(classeRepository, niveauRepository, filiereRepository, anneeScolaireRepository,
                siteQuery, entityManager);
        etablissementId = UUID.randomUUID();
        sitePrincipalId = UUID.randomUUID();
        portee = ContexteEtablissement.ouvrir(etablissementId);

        when(classeRepository.save(any(Classe.class))).thenAnswer(inv -> inv.getArgument(0));
        when(siteQuery.idSitePrincipal()).thenReturn(sitePrincipalId);
        when(anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.of(anneeActive()));
    }

    @AfterEach
    void fermerContexte() {
        portee.close();
    }

    private Cycle cycle(UUID id) {
        Cycle cycle = new Cycle(etablissementId, "Collège", null, 1);
        ReflectionTestUtils.setField(cycle, "id", id);
        return cycle;
    }

    private Niveau niveauActif(UUID id, Cycle cycle) {
        Niveau niveau = new Niveau(etablissementId, "6ème", null, 1, cycle);
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

    private AnneeScolaire anneeActive() {
        AnneeScolaire annee = new AnneeScolaire(etablissementId, "2026-2027",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15));
        ReflectionTestUtils.setField(annee, "id", UUID.randomUUID());
        annee.activer(java.time.Instant.now());
        return annee;
    }

    private AnneeScolaire anneeCloturee() {
        AnneeScolaire annee = new AnneeScolaire(etablissementId, "2025-2026",
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 7, 15));
        ReflectionTestUtils.setField(annee, "id", UUID.randomUUID());
        annee.activer(java.time.Instant.now());
        annee.cloturer(java.time.Instant.now());
        return annee;
    }

    private Classe classeAvecId(UUID id, Niveau niveau, Filiere filiere, AnneeScolaire annee, UUID siteId) {
        Classe classe = new Classe(etablissementId, "6ème A", "A", niveau, filiere, annee, siteId, 40);
        ReflectionTestUtils.setField(classe, "id", id);
        return classe;
    }

    // ------------------------------------------------------------------
    // R7 : niveau obligatoire, existant et actif
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandNiveauIntrouvable() {
        UUID niveauId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.empty());
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, null, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.NIVEAU_INTROUVABLE);
    }

    @Test
    void doitRejeterCreation_quandNiveauInactif() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauInactif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, null, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.CLASSE_REFERENTIEL_INACTIF);
    }

    // ------------------------------------------------------------------
    // R8 : année par défaut = année active, clôturée -> refus
    // ------------------------------------------------------------------

    @Test
    void doitUtiliserLanneeActiveParDefaut_quandAnneeScolaireIdAbsent() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, null, 40);

        Classe creee = service.creer(requete);

        assertThat(creee.getAnneeScolaire().getStatut()).isEqualTo(StatutAnneeScolaire.ACTIVE);
    }

    @Test
    void doitRejeterCreation_quandAnneeScolaireCloturee() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID anneeId = UUID.randomUUID();
        AnneeScolaire cloturee = anneeCloturee();
        ReflectionTestUtils.setField(cloturee, "id", anneeId);
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        when(anneeScolaireRepository.findById(anneeId)).thenReturn(Optional.of(cloturee));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, anneeId, null, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.CLASSE_ANNEE_CLOTUREE);
    }

    @Test
    void doitRejeterCreation_quandAucuneAnneeActiveEtAnneeScolaireIdAbsent() {
        when(anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.empty());
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, null, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.ANNEE_SCOLAIRE_ACTIVE_ABSENTE);
    }

    // ------------------------------------------------------------------
    // R9 : filière optionnelle, doit exister et être active si fournie
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandFiliereFournieIntrouvable() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID filiereId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        when(filiereRepository.findById(filiereId)).thenReturn(Optional.empty());
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, filiereId, null, null, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_INTROUVABLE);
    }

    // ------------------------------------------------------------------
    // R10 : cohérence filière/cycle
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandFiliereAppartientAUnAutreCycleQueCeluiDuNiveau() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleDuNiveau = UUID.randomUUID();
        UUID cycleDeLaFiliere = UUID.randomUUID();
        UUID filiereId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleDuNiveau))));
        when(filiereRepository.findById(filiereId))
                .thenReturn(Optional.of(filiereActive(filiereId, cycle(cycleDeLaFiliere))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("Tle D", "D", niveauId, filiereId, null, null, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.FILIERE_CYCLE_INCOHERENT);
    }

    @Test
    void doitAccepterCreation_quandFiliereSansCycle() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleDuNiveau = UUID.randomUUID();
        UUID filiereId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleDuNiveau))));
        when(filiereRepository.findById(filiereId)).thenReturn(Optional.of(filiereActive(filiereId, null)));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("Tle D", "D", niveauId, filiereId, null, null, 40);

        Classe creee = service.creer(requete);

        assertThat(creee.getFiliere().getId()).isEqualTo(filiereId);
    }

    @Test
    void doitAccepterCreation_quandFiliereAppartientAuMemeCycleQueLeNiveau() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID filiereId = UUID.randomUUID();
        Cycle cycleCommun = cycle(cycleId);
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycleCommun)));
        when(filiereRepository.findById(filiereId)).thenReturn(Optional.of(filiereActive(filiereId, cycleCommun)));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("Tle D", "D", niveauId, filiereId, null, null, 40);

        Classe creee = service.creer(requete);

        assertThat(creee.getFiliere().getId()).isEqualTo(filiereId);
    }

    // ------------------------------------------------------------------
    // R11 : site_id, point le plus sensible de l'US (isolation, D3)
    // ------------------------------------------------------------------

    @Test
    void doitResoudreLeSitePrincipal_quandSiteIdAbsent() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, null, 40);

        Classe creee = service.creer(requete);

        assertThat(creee.getSiteId()).isEqualTo(sitePrincipalId);
    }

    /**
     * Le test le plus important de cette US (arbitrage tech lead) : un
     * {@code site_id} qui n'appartient pas à l'établissement courant doit être
     * refusé, même s'il est syntaxiquement valide. C'est le seul identifiant
     * de requête que le filtre Hibernate ne protège pas.
     *
     * <p>Contre-épreuve manuelle effectuée : en retirant la garde
     * {@code siteQuery.existeDansEtablissementCourant(...)} dans
     * {@code ClasseService.resoudreSite}, ce test échoue (le site étranger est
     * accepté tel quel) — la garde est donc bien ce qui fait passer ce test,
     * pas un effet de bord d'une autre règle.</p>
     */
    @Test
    void doitRejeterCreation_quandSiteIdAppartientAUnAutreEtablissement() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID siteDunAutreEtablissement = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        when(siteQuery.existeDansEtablissementCourant(siteDunAutreEtablissement)).thenReturn(false);
        CreerClasseRequestDto requete =
                new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, siteDunAutreEtablissement, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.CLASSE_SITE_INVALIDE);
    }

    @Test
    void doitAccepterCreation_quandSiteIdAppartientALetablissementCourant() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID siteValide = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        when(siteQuery.existeDansEtablissementCourant(siteValide)).thenReturn(true);
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, siteValide, 40);

        Classe creee = service.creer(requete);

        assertThat(creee.getSiteId()).isEqualTo(siteValide);
    }

    // ------------------------------------------------------------------
    // R12 : classe immuable si année clôturée
    // ------------------------------------------------------------------

    @Test
    void doitRejeterModification_quandAnneeDeLaClasseCloturee() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID classeId = UUID.randomUUID();
        Niveau niveau = niveauActif(niveauId, cycle(cycleId));
        Classe classe = classeAvecId(classeId, niveau, null, anneeCloturee(), sitePrincipalId);
        when(classeRepository.findWithGraphById(classeId)).thenReturn(Optional.of(classe));
        ModifierClasseRequestDto requete = new ModifierClasseRequestDto("6ème A", "A", niveauId, null, null, 40);

        assertThatThrownBy(() -> service.modifier(classeId, requete))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.CLASSE_ANNEE_CLOTUREE);
    }

    @Test
    void doitRejeterDesactivation_quandAnneeDeLaClasseCloturee() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID classeId = UUID.randomUUID();
        Niveau niveau = niveauActif(niveauId, cycle(cycleId));
        Classe classe = classeAvecId(classeId, niveau, null, anneeCloturee(), sitePrincipalId);
        when(classeRepository.findWithGraphById(classeId)).thenReturn(Optional.of(classe));

        assertThatThrownBy(() -> service.desactiver(classeId))
                .isInstanceOf(RegleMetierViolee.class)
                .extracting(e -> ((RegleMetierViolee) e).getCode())
                .isEqualTo(CodeErreur.CLASSE_ANNEE_CLOTUREE);
    }

    @Test
    void doitAutoriserModification_quandAnneeDeLaClasseActive() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        UUID classeId = UUID.randomUUID();
        Niveau niveau = niveauActif(niveauId, cycle(cycleId));
        Classe classe = classeAvecId(classeId, niveau, null, anneeActive(), sitePrincipalId);
        when(classeRepository.findWithGraphById(classeId)).thenReturn(Optional.of(classe));
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveau));
        ModifierClasseRequestDto requete = new ModifierClasseRequestDto("6ème B", "B", niveauId, null, null, 45);

        Classe modifiee = service.modifier(classeId, requete);

        assertThat(modifiee.getLibelle()).isEqualTo("6ème B");
        assertThat(modifiee.getEffectifMax()).isEqualTo(45);
    }

    // ------------------------------------------------------------------
    // D5 : libellé de classe libre (fourni tel quel) ou composé si absent
    // ------------------------------------------------------------------

    @Test
    void doitConserverLeLibelleFourniTelQuel_sansImposerDeFormat() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("Grade 6 A", null, niveauId, null, null, null, null);

        Classe creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("Grade 6 A");
    }

    @Test
    void doitComposerLeLibelle_quandLibelleAbsentAvecSuffixe() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto(null, "A", niveauId, null, null, null, null);

        Classe creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("6ème A");
    }

    @Test
    void doitComposerLeLibelleSansSuffixe_quandLibelleEtSuffixeAbsents() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto(null, null, niveauId, null, null, null, null);

        Classe creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("6ème");
    }

    @Test
    void doitComposerLeLibelle_quandLibelleFourniEstBlanc() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        CreerClasseRequestDto requete = new CreerClasseRequestDto("   ", "A", niveauId, null, null, null, null);

        Classe creee = service.creer(requete);

        assertThat(creee.getLibelle()).isEqualTo("6ème A");
    }

    // ------------------------------------------------------------------
    // Libellé unique par (établissement, année) parmi les classes actives
    // ------------------------------------------------------------------

    @Test
    void doitRejeterCreation_quandLibelleDejaUtilisePourCetteAnnee() {
        UUID niveauId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        AnneeScolaire annee = anneeActive();
        when(niveauRepository.findById(niveauId)).thenReturn(Optional.of(niveauActif(niveauId, cycle(cycleId))));
        when(anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE))
                .thenReturn(Optional.of(annee));
        when(classeRepository.existsByEtablissementIdAndAnneeScolaireIdAndLibelleAndActifTrue(
                etablissementId, annee.getId(), "6ème A")).thenReturn(true);
        CreerClasseRequestDto requete = new CreerClasseRequestDto("6ème A", "A", niveauId, null, null, null, 40);

        assertThatThrownBy(() -> service.creer(requete))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode())
                .isEqualTo(CodeErreur.CLASSE_LIBELLE_DUPLIQUE);
    }

    // ------------------------------------------------------------------
    // Cas limite transverse : ressource introuvable
    // ------------------------------------------------------------------

    @Test
    void doitRejeterObtenir_quandClasseIntrouvable() {
        UUID id = UUID.randomUUID();
        when(classeRepository.findWithGraphById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id))
                .isInstanceOf(RessourceIntrouvableException.class)
                .extracting(e -> ((RessourceIntrouvableException) e).getCode())
                .isEqualTo(CodeErreur.CLASSE_INTROUVABLE);
    }
}
