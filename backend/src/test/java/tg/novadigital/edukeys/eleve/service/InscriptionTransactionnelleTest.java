package tg.novadigital.edukeys.eleve.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import tg.novadigital.edukeys.academique.ClasseInscriptionQuery;
import tg.novadigital.edukeys.academique.ClasseInscriptionQuery.ClassePourInscription;
import tg.novadigital.edukeys.academique.OffreAdmissionQuery;
import tg.novadigital.edukeys.admission.DossierAdmissionInscription;
import tg.novadigital.edukeys.admission.DossierAdmissionInscription.DossierPourInscription;
import tg.novadigital.edukeys.common.domain.BaseEntity;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.EdukeysException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.eleve.domain.Eleve;
import tg.novadigital.edukeys.eleve.domain.Inscription;
import tg.novadigital.edukeys.eleve.repository.EleveRepository;
import tg.novadigital.edukeys.eleve.repository.InscriptionRepository;
import tg.novadigital.edukeys.etablissement.EtablissementCourantQuery;
import tg.novadigital.edukeys.identite.CreateurCompteEleve;
import tg.novadigital.edukeys.identite.EmetteurMotDePasseTemporaire;

/** Ordre des vérifications et effets de {@link InscriptionTransactionnelle}, ports simulés (US-08). */
class InscriptionTransactionnelleTest {

    private static final UUID ETABLISSEMENT = UUID.randomUUID();
    private static final UUID DEMANDE = UUID.randomUUID();
    private static final UUID ANNEE = UUID.randomUUID();
    private static final UUID NIVEAU = UUID.randomUUID();
    private static final UUID CLASSE = UUID.randomUUID();
    private static final UUID SITE = UUID.randomUUID();
    private static final UUID COMPTE = UUID.randomUUID();
    private static final Instant MAINTENANT = Instant.parse("2026-05-15T10:00:00Z");

    private DossierAdmissionInscription dossierPort;
    private ClasseInscriptionQuery classePort;
    private EtablissementCourantQuery etablissementPort;
    private OffreAdmissionQuery libellesPort;
    private EleveRepository eleveRepository;
    private InscriptionRepository inscriptionRepository;
    private GenerateurMatricule generateurMatricule;
    private CreateurCompteEleve createurCompte;
    private EmetteurMotDePasseTemporaire emetteur;
    private EntityManager entityManager;
    private InscriptionTransactionnelle service;

    @BeforeEach
    void configurer() {
        dossierPort = mock(DossierAdmissionInscription.class);
        classePort = mock(ClasseInscriptionQuery.class);
        etablissementPort = mock(EtablissementCourantQuery.class);
        libellesPort = mock(OffreAdmissionQuery.class);
        eleveRepository = mock(EleveRepository.class);
        inscriptionRepository = mock(InscriptionRepository.class);
        generateurMatricule = mock(GenerateurMatricule.class);
        createurCompte = mock(CreateurCompteEleve.class);
        emetteur = mock(EmetteurMotDePasseTemporaire.class);
        entityManager = mock(EntityManager.class);
        service = new InscriptionTransactionnelle(dossierPort, classePort, etablissementPort, libellesPort, eleveRepository,
                inscriptionRepository, generateurMatricule, createurCompte, emetteur,
                new PolitiqueExpirationMotDePasseEleve(14, 30), Clock.fixed(MAINTENANT, ZoneOffset.UTC), entityManager);

        when(dossierPort.verrouillerPourInscription(DEMANDE)).thenReturn(dossier(3, true, null));
        when(classePort.verrouillerPourInscription(CLASSE)).thenReturn(classe(true, ANNEE, NIVEAU, true, 30));
        when(etablissementPort.parametresInscription()).thenReturn(new EtablissementCourantQuery.ParametresInscription("CSJ", "Africa/Lome"));
        when(eleveRepository.rechercherHomonymes(any(), anyString(), anyString(), any())).thenReturn(List.of());
        when(inscriptionRepository.countByClasseIdAndActifTrue(CLASSE)).thenReturn(0L);
        when(generateurMatricule.generer(ETABLISSEMENT, "CSJ", 2026)).thenReturn("CSJ-2026-00001");
        when(createurCompte.creerCompteEleve("CSJ-2026-00001", "KODJO Ama")).thenReturn(COMPTE);
        when(emetteur.emettreAvecExpiration(eq(COMPTE), any())).thenReturn("Temp-Secret-1");
        when(eleveRepository.save(any(Eleve.class))).thenAnswer(i -> avecId(i.getArgument(0)));
        when(inscriptionRepository.save(any(Inscription.class))).thenAnswer(i -> avecId(i.getArgument(0)));
    }

    private static <T extends BaseEntity> T avecId(T entite) throws Exception {
        Field champ = BaseEntity.class.getDeclaredField("id");
        champ.setAccessible(true);
        champ.set(entite, UUID.randomUUID());
        return entite;
    }

    private static DossierPourInscription dossier(long version, boolean acceptee, UUID eleveId) {
        return new DossierPourInscription(DEMANDE, version, acceptee ? "ACCEPTEE" : "EN_ATTENTE", acceptee, eleveId, ANNEE, NIVEAU, null,
                "Kodjo", "Ama", LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null);
    }

    private static ClassePourInscription classe(boolean actif, UUID annee, UUID niveau, boolean anneeModifiable, Integer effectifMax) {
        return new ClassePourInscription(CLASSE, "6ème A", actif, niveau, "6ème", null, null, annee, "2026-2027",
                LocalDate.of(2026, 9, 1), anneeModifiable, SITE, effectifMax);
    }

    private ResultatInscription inscrire(boolean confirmerHomonyme) {
        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT)) {
            return service.executer(new CommandeInscription(DEMANDE, CLASSE, 3, confirmerHomonyme));
        }
    }

    private void assertRefus(Class<? extends EdukeysException> type, CodeErreur code) {
        assertThatThrownBy(() -> inscrire(false))
                .isInstanceOfSatisfying(type, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private void aucuneEcriture() {
        verify(generateurMatricule, never()).generer(any(), anyString(), anyInt());
        verify(createurCompte, never()).creerCompteEleve(anyString(), anyString());
        verify(eleveRepository, never()).save(any());
        verify(inscriptionRepository, never()).save(any());
        verify(emetteur, never()).emettreAvecExpiration(any(), any());
        verify(dossierPort, never()).marquerInscrite(any(), any(), any());
    }

    @Test
    void cheminNominal_executeLesEtapesDansLOrdre_etRendLeResultat() {
        ResultatInscription r = inscrire(false);

        InOrder ordre = inOrder(dossierPort, classePort, generateurMatricule, createurCompte, emetteur, entityManager);
        ordre.verify(dossierPort).verrouillerPourInscription(DEMANDE);
        ordre.verify(classePort).verrouillerPourInscription(CLASSE);
        ordre.verify(generateurMatricule).generer(ETABLISSEMENT, "CSJ", 2026);
        ordre.verify(createurCompte).creerCompteEleve("CSJ-2026-00001", "KODJO Ama");
        ordre.verify(emetteur).emettreAvecExpiration(eq(COMPTE), any());
        ordre.verify(dossierPort).marquerInscrite(eq(DEMANDE), any(UUID.class), eq(MAINTENANT));
        ordre.verify(entityManager).flush();

        assertThat(r.matricule()).isEqualTo("CSJ-2026-00001");
        assertThat(r.nom()).isEqualTo("Kodjo");
        assertThat(r.classe().libelle()).isEqualTo("6ème A");
        assertThat(r.filiere()).isNull();
        assertThat(r.siteId()).isEqualTo(SITE);
        assertThat(r.dateInscription()).isEqualTo(MAINTENANT);
        assertThat(r.compte().identifiantConnexion()).isEqualTo("csj-2026-00001");
        assertThat(r.compte().motDePasseTemporaire()).isEqualTo("Temp-Secret-1");
        assertThat(r.compte().toString()).doesNotContain("Temp-Secret-1");
    }

    @Test
    void inscriptionDu15Mai_pourUneRentreeDu1erSeptembre_demandeUneExpirationAu1erOctobre() {
        inscrire(false);

        verify(emetteur).emettreAvecExpiration(COMPTE, Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void anneeDuMatricule_estLAnneeDeDebutDeLAnneeScolaire() {
        when(classePort.verrouillerPourInscription(CLASSE)).thenReturn(new ClassePourInscription(CLASSE, "6ème A", true, NIVEAU, "6ème",
                null, null, ANNEE, "2026-2027", LocalDate.of(2026, 9, 1), true, SITE, null));

        inscrire(false);

        verify(generateurMatricule).generer(ETABLISSEMENT, "CSJ", 2026);
    }

    @Test
    void dossierIntrouvable_404_avantTouteAutreChose() {
        when(dossierPort.verrouillerPourInscription(DEMANDE)).thenThrow(new RessourceIntrouvableException(CodeErreur.ADMISSION_INTROUVABLE, "x"));

        assertThatThrownBy(() -> inscrire(false)).isInstanceOf(RessourceIntrouvableException.class);
        verify(classePort, never()).verrouillerPourInscription(any());
        aucuneEcriture();
    }

    @Test
    void versionPerimee_409_sansVerrouDeClasse() {
        when(dossierPort.verrouillerPourInscription(DEMANDE)).thenReturn(dossier(4, true, null));

        assertRefus(ConflitException.class, CodeErreur.ADMISSION_MODIFICATION_CONCURRENTE);
        verify(classePort, never()).verrouillerPourInscription(any());
        aucuneEcriture();
    }

    @Test
    void dossierNonAccepte_422() {
        when(dossierPort.verrouillerPourInscription(DEMANDE)).thenReturn(dossier(3, false, null));

        assertRefus(RegleMetierViolee.class, CodeErreur.INSCRIPTION_DEMANDE_NON_ACCEPTEE);
        aucuneEcriture();
    }

    @Test
    void dossierDejaInscrit_409() {
        when(dossierPort.verrouillerPourInscription(DEMANDE)).thenReturn(dossier(3, true, UUID.randomUUID()));

        assertRefus(ConflitException.class, CodeErreur.INSCRIPTION_DEJA_EFFECTUEE);
        verify(classePort, never()).verrouillerPourInscription(any());
        aucuneEcriture();
    }

    @Test
    void classeInactive_422() {
        when(classePort.verrouillerPourInscription(CLASSE)).thenReturn(classe(false, ANNEE, NIVEAU, true, 30));

        assertRefus(RegleMetierViolee.class, CodeErreur.INSCRIPTION_CLASSE_INACTIVE);
        aucuneEcriture();
    }

    @Test
    void anneeDeLaClasseDifferenteDeCelleDuDossier_422_avantLeControleDeCloture() {
        when(classePort.verrouillerPourInscription(CLASSE)).thenReturn(classe(true, UUID.randomUUID(), NIVEAU, false, 30));

        assertRefus(RegleMetierViolee.class, CodeErreur.INSCRIPTION_ANNEE_INCOHERENTE);
        aucuneEcriture();
    }

    @Test
    void anneeCloturee_422() {
        when(classePort.verrouillerPourInscription(CLASSE)).thenReturn(classe(true, ANNEE, NIVEAU, false, 30));

        assertRefus(RegleMetierViolee.class, CodeErreur.INSCRIPTION_ANNEE_CLOTUREE);
        aucuneEcriture();
    }

    @Test
    void niveauDifferent_422() {
        when(classePort.verrouillerPourInscription(CLASSE)).thenReturn(classe(true, ANNEE, UUID.randomUUID(), true, 30));

        assertRefus(RegleMetierViolee.class, CodeErreur.INSCRIPTION_NIVEAU_INCOHERENT);
        aucuneEcriture();
    }

    @Test
    void homonymeNonConfirme_409AvecLesDetails_puisConfirmeEstAccepte() {
        when(eleveRepository.rechercherHomonymes(ETABLISSEMENT, "KODJO", "AMA", LocalDate.of(2015, 5, 12)))
                .thenReturn(List.of(new EleveRepository.Homonyme("CSJ-2025-00007", CLASSE)));
        when(libellesPort.libelles(anySet())).thenReturn(Map.of(CLASSE, "6ème A"));

        assertThatThrownBy(() -> inscrire(false))
                .isInstanceOfSatisfying(ConflitException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(CodeErreur.ELEVE_HOMONYME);
                    assertThat(e.getDetails()).containsKey("homonymes");
                    assertThat(e.getDetails().get("homonymes").toString()).contains("CSJ-2025-00007").contains("6ème A");
                });
        aucuneEcriture();

        assertThat(inscrire(true).matricule()).isEqualTo("CSJ-2026-00001");
    }

    @Test
    void classePleine_422_etLeMatriculeNEstPasConsomme() {
        when(inscriptionRepository.countByClasseIdAndActifTrue(CLASSE)).thenReturn(30L);

        assertRefus(RegleMetierViolee.class, CodeErreur.INSCRIPTION_CLASSE_COMPLETE);
        aucuneEcriture();
    }

    @Test
    void derniereplaceDisponible_estAttribuee() {
        when(inscriptionRepository.countByClasseIdAndActifTrue(CLASSE)).thenReturn(29L);

        assertThat(inscrire(false).matricule()).isNotBlank();
    }

    @Test
    void effectifMaxNull_neLimitePas_etNeCompteMemePas() {
        when(classePort.verrouillerPourInscription(CLASSE)).thenReturn(classe(true, ANNEE, NIVEAU, true, null));

        assertThat(inscrire(false).matricule()).isNotBlank();
        verify(inscriptionRepository, never()).countByClasseIdAndActifTrue(any());
    }

    @Test
    void expirationHorsBornes_422_propageeSansMarquerLeDossier() {
        when(emetteur.emettreAvecExpiration(eq(COMPTE), any())).thenThrow(
                new RegleMetierViolee(CodeErreur.MOT_DE_PASSE_TEMPORAIRE_EXPIRATION_HORS_BORNES, "trop anticipée"));

        assertRefus(RegleMetierViolee.class, CodeErreur.MOT_DE_PASSE_TEMPORAIRE_EXPIRATION_HORS_BORNES);
        verify(dossierPort, never()).marquerInscrite(any(), any(), any());
    }

    @Test
    void identifiantDeCompteDejaPris_409_apresLIncrement_sansAutreEcriture() {
        when(createurCompte.creerCompteEleve(anyString(), anyString())).thenThrow(
                new ConflitException(CodeErreur.UTILISATEUR_IDENTIFIANT_DUPLIQUE, "pris"));

        assertRefus(ConflitException.class, CodeErreur.UTILISATEUR_IDENTIFIANT_DUPLIQUE);
        verify(eleveRepository, never()).save(any());
        verify(dossierPort, never()).marquerInscrite(any(), any(), any());
    }

    @Test
    void violationDUniciteDuLienDossierEleveAuFlush_devient409DejaInscrit() {
        for (String contrainte : new String[] {"uk_eleves_demande_admission", "uk_demandes_admission_eleve"}) {
            org.mockito.Mockito.doThrow(violation(contrainte)).when(entityManager).flush();
            assertRefus(ConflitException.class, CodeErreur.INSCRIPTION_DEJA_EFFECTUEE);
        }
    }

    @Test
    void violationDeLIdentifiantAuFlush_devient409IdentifiantDuplique_etUneAutreViolationRemonteTelle() {
        org.mockito.Mockito.doThrow(violation("uk_utilisateurs_identifiant_connexion_actif")).when(entityManager).flush();
        assertRefus(ConflitException.class, CodeErreur.UTILISATEUR_IDENTIFIANT_DUPLIQUE);

        org.mockito.Mockito.doThrow(violation("uk_eleves_matricule")).when(entityManager).flush();
        assertThatThrownBy(() -> inscrire(false)).isInstanceOf(PersistenceException.class);
    }

    private static PersistenceException violation(String contrainte) {
        return new PersistenceException(new org.hibernate.exception.ConstraintViolationException(
                "violation", new SQLException("dup", "23505"), contrainte));
    }
}
