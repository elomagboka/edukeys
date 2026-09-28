package tg.novadigital.edukeys.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import tg.novadigital.edukeys.academique.OffreAdmissionQuery;
import tg.novadigital.edukeys.admission.AdmissionProperties;
import tg.novadigital.edukeys.admission.domain.CanalAdmission;
import tg.novadigital.edukeys.admission.domain.DecisionAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.LienResponsable;
import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.admission.domain.StatutDecisionAdmission;
import tg.novadigital.edukeys.admission.repository.DemandeAdmissionRepository;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.securite.PrincipalAuditable;
import tg.novadigital.edukeys.etablissement.EtablissementPublicQuery;

/**
 * Cœur métier de la pré-inscription (US-06) : idempotence, honeypot, statut
 * imposé, atomicité des pièces. La vérification Turnstile n'est plus de la
 * responsabilité de ce service depuis la revue B2+I1 (déplacée dans
 * {@code FiltreVerificationTurnstileAdmission}, testée séparément). Depuis
 * I5 (revue), {@link DemandeAdmissionService} est un orchestrateur non
 * transactionnel : les étapes qui touchent la base sont déléguées à
 * {@link InsertionDemandeAdmissionTransactionnelle}, mockée ici — la
 * résolution d'offre et la vérification de doublon en base ne sont donc
 * plus exercées par ce test unitaire (voir le test d'intégration pour le
 * chemin HTTP complet, y compris la course concurrente).
 */
@ExtendWith(MockitoExtension.class)
class DemandeAdmissionServiceTest {

    private DemandeAdmissionRepository demandeAdmissionRepository;
    private tg.novadigital.edukeys.admission.repository.DecisionAdmissionRepository decisionAdmissionRepository;
    private EtablissementPublicQuery etablissementPublicQuery;
    private OffreAdmissionQuery offreAdmissionQuery;
    private StockagePiecesJointes stockagePiecesJointes;
    private AdmissionProperties proprietes;
    private InsertionDemandeAdmissionTransactionnelle insertionTransactionnelle;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private jakarta.persistence.EntityManager entityManager;
    private DemandeAdmissionService service;

    private final UUID etablissementId = UUID.randomUUID();
    private final UUID anneeId = UUID.randomUUID();
    private final UUID niveauId = UUID.randomUUID();

    @BeforeEach
    void avantChaqueTest() {
        demandeAdmissionRepository = mock(DemandeAdmissionRepository.class);
        decisionAdmissionRepository = mock(tg.novadigital.edukeys.admission.repository.DecisionAdmissionRepository.class);
        etablissementPublicQuery = mock(EtablissementPublicQuery.class);
        offreAdmissionQuery = mock(OffreAdmissionQuery.class);
        stockagePiecesJointes = mock(StockagePiecesJointes.class);
        proprietes = new AdmissionProperties();
        insertionTransactionnelle = mock(InsertionDemandeAdmissionTransactionnelle.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        entityManager = mock(jakarta.persistence.EntityManager.class);
        service = new DemandeAdmissionService(
                demandeAdmissionRepository, decisionAdmissionRepository, etablissementPublicQuery, offreAdmissionQuery,
                stockagePiecesJointes, proprietes, insertionTransactionnelle, eventPublisher, entityManager);
    }

    private CommandeDemandeAdmission donneesValides() {
        return new CommandeDemandeAdmission(
                niveauId, null, "Kodjo", "Ama", LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null,
                "Kodjo", "Père", LienResponsable.PERE, "+22890000001", "responsable@example.com", true);
    }

    private void arrangerEtablissementOuvert() {
        when(etablissementPublicQuery.resoudreParCode("ecole"))
                .thenReturn(Optional.of(new EtablissementPublicQuery.EtablissementPublic(etablissementId, "École Test", null, true)));
    }

    private void arrangerAucunDoublon() {
        when(insertionTransactionnelle.resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any()))
                .thenReturn(new InsertionDemandeAdmissionTransactionnelle.OffreEtDoublon(anneeId, null));
    }

    // ------------------------------------------------------------------
    // Honeypot (règle 6 / critère 8)
    // ------------------------------------------------------------------

    @Test
    void doitRenvoyerUneReferenceFictive_quandHoneypotRempli_sansRienEnregistrer() {
        DemandeAdmissionService.Accuse accuse = service.soumettrePublique(
                "ecole", donneesValides(), "http://spam.example", null, null, "41.207.0.1");

        assertThat(accuse.reference()).isEqualTo("PRE-0000-000000");
        assertThat(accuse.codeSuivi()).isEqualTo("0".repeat(26));
        assertThat(accuse.nouveau()).isTrue();
        verify(insertionTransactionnelle, never()).resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any());
        verify(insertionTransactionnelle, never()).inserer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Idempotence (critère 1, le cas central) — I5 : comparaison normalisée
    // ------------------------------------------------------------------

    @Test
    void doitRenvoyerLeDossierExistant_quandUneDemandeIdentiqueEstDejaEnAttente_sansDupliquer() {
        arrangerEtablissementOuvert();
        DemandeAdmission existante = new DemandeAdmission(
                etablissementId, "PRE-2026-000042", "CS-000042", anneeId, niveauId, null, "Kodjo", "Ama",
                LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null, "Kodjo", "Père", LienResponsable.PERE,
                "+22890000001", "responsable@example.com", CanalAdmission.PUBLIC, Instant.now(),
                Instant.now(), "hash");
        when(insertionTransactionnelle.resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any()))
                .thenReturn(new InsertionDemandeAdmissionTransactionnelle.OffreEtDoublon(anneeId, existante));

        // Pièce valide jointe : depuis I4 (2e revue), les pièces sont validées avant
        // la recherche de doublon, un envoi sans pièce serait refusé d'abord.
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        DemandeAdmissionService.Accuse accuse = service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1");

        assertThat(accuse.reference()).isEqualTo("PRE-2026-000042");
        assertThat(accuse.codeSuivi()).isEqualTo("CS-000042");
        assertThat(accuse.nouveau()).isFalse();
        verify(insertionTransactionnelle, never()).inserer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * I5 (2e revue) : le test de concurrence réelle peut passer sans jamais
     * emprunter la récupération (si la première transaction committe avant que
     * la seconde ne cherche le doublon). Celui-ci force l'ordre : les deux
     * soumissions ont passé l'étape 1, l'insertion viole l'index unique.
     */
    @Test
    void renvoieLeDossierExistant_quandLinsertionVioleLindexDeDoublon_soumissionConcurrente() {
        arrangerEtablissementOuvert();
        arrangerAucunDoublon();
        when(insertionTransactionnelle.inserer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(violationDe(InsertionDemandeAdmissionTransactionnelle.CONTRAINTE_DOUBLON));
        DemandeAdmission gagnante = new DemandeAdmission(
                etablissementId, "PRE-2026-000077", "CS-000077", anneeId, niveauId, null, "Kodjo", "Ama",
                LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null, "Kodjo", "Père", LienResponsable.PERE,
                "+22890000001", "responsable@example.com", CanalAdmission.PUBLIC, Instant.now(), Instant.now(), "hash");
        when(insertionTransactionnelle.relireApresConflit(any(), any(), any(), any(), any())).thenReturn(gagnante);

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        DemandeAdmissionService.Accuse accuse = service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1");

        assertThat(accuse.reference()).isEqualTo("PRE-2026-000077");
        assertThat(accuse.nouveau()).isFalse();
        verify(insertionTransactionnelle).relireApresConflit(any(), any(), any(), any(), any());
    }

    /** Toute autre violation d'intégrité remonte : la récupération ne rattrape que l'index de doublon. */
    @Test
    void laisseRemonterUneAutreViolationDIntegrite_sansTenterDeRelire() {
        arrangerEtablissementOuvert();
        arrangerAucunDoublon();
        when(insertionTransactionnelle.inserer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(violationDe("uk_demandes_admission_reference"));

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(insertionTransactionnelle, never()).relireApresConflit(any(), any(), any(), any(), any());
    }

    private static DataIntegrityViolationException violationDe(String contrainte) {
        return new DataIntegrityViolationException("violation simulée",
                new ConstraintViolationException(
                        "violation simulée", new SQLException("duplicate key", "23505"), contrainte));
    }

    // ------------------------------------------------------------------
    // Statut imposé par le serveur (critère 3)
    // ------------------------------------------------------------------

    @Test
    void doitCreerLeDossierAuStatutEnAttente_memeSiAucunStatutNestFourniParLeClient() {
        arrangerEtablissementOuvert();
        arrangerAucunDoublon();
        DemandeAdmission sauvegardee = new DemandeAdmission(
                etablissementId, "PRE-2026-000001", "CS-000001", anneeId, niveauId, null, "Kodjo", "Ama",
                LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null, "Kodjo", "Père", LienResponsable.PERE,
                "+22890000001", "responsable@example.com", CanalAdmission.PUBLIC, Instant.now(), Instant.now(), "hash");
        when(insertionTransactionnelle.inserer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sauvegardee);

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        DemandeAdmissionService.Accuse accuse = service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1");

        assertThat(accuse.reference()).isEqualTo("PRE-2026-000001");
        assertThat(accuse.nouveau()).isTrue();
    }

    // ------------------------------------------------------------------
    // Consentement (mineur : code dédié, plus ADMISSION_CHOIX_NIVEAU_CLASSE_INVALIDE)
    // ------------------------------------------------------------------

    @Test
    void doitRefuserSoumission_quandLeConsentementEstAbsent() {
        arrangerEtablissementOuvert();
        CommandeDemandeAdmission sansConsentement = new CommandeDemandeAdmission(
                niveauId, null, "Kodjo", "Ama", LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null,
                "Kodjo", "Père", LienResponsable.PERE, "+22890000001", "responsable@example.com", false);

        assertThatThrownBy(() -> service.soumettrePublique("ecole", sansConsentement, null, null, null, "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_CONSENTEMENT_MANQUANT));
    }

    // ------------------------------------------------------------------
    // Choix niveau/classe et offre fermée (critère 11) — désormais portés
    // par InsertionDemandeAdmissionTransactionnelle, mockée ici.
    // ------------------------------------------------------------------

    @Test
    void doitRefuserSoumission_quandLeChoixNiveauClasseEstInvalide() {
        arrangerEtablissementOuvert();
        when(insertionTransactionnelle.resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RegleMetierViolee(CodeErreur.ADMISSION_CHOIX_NIVEAU_CLASSE_INVALIDE, "invalide"));

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_CHOIX_NIVEAU_CLASSE_INVALIDE));
    }

    @Test
    void doitRefuserSoumission_quandAucuneAnneeOuverteAlAdmission() {
        arrangerEtablissementOuvert();
        when(insertionTransactionnelle.resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RegleMetierViolee(CodeErreur.ADMISSION_FERMEE, "fermee"));

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_FERMEE));
    }

    @Test
    void doitRefuserSoumission_quandEtablissementIntrouvable() {
        when(etablissementPublicQuery.resoudreParCode("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.soumettrePublique("inconnu", donneesValides(), null, null, null, "41.207.0.1"))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void doitRefuserSoumission_quandAdmissionsFermeesPourLetablissement() {
        when(etablissementPublicQuery.resoudreParCode("ecole"))
                .thenReturn(Optional.of(new EtablissementPublicQuery.EtablissementPublic(etablissementId, "École Test", null, false)));

        assertThatThrownBy(() -> service.soumettrePublique("ecole", donneesValides(), null, null, null, "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_FERMEE));
    }

    // ------------------------------------------------------------------
    // Pièces : atomicité et acte de naissance obligatoire (critères 6 et 7)
    // ------------------------------------------------------------------

    @Test
    void doitRefuserSoumission_quandActeNaissanceAbsent() {
        arrangerEtablissementOuvert();

        MockMultipartFile piece = new MockMultipartFile("pieces", "bulletin.pdf", "application/pdf", pdfMinimal());

        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("BULLETIN"), "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_ACTE_NAISSANCE_MANQUANT));
        verify(insertionTransactionnelle, never()).inserer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // I4 (2e revue) : refus avant toute recherche de doublon, dossier existant ou non.
        verify(insertionTransactionnelle, never()).resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void neDoitRienEnregistrer_quandUnePieceEstVide_atomiciteDuDossier() {
        arrangerEtablissementOuvert();

        MockMultipartFile acteValide = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        MockMultipartFile pieceVide = new MockMultipartFile("pieces", "vide.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(acteValide, pieceVide), List.of("ACTE_NAISSANCE", "BULLETIN"), "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_PIECE_VIDE));

        verify(insertionTransactionnelle, never()).inserer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // I4 (2e revue) : refus avant toute recherche de doublon, dossier existant ou non.
        verify(insertionTransactionnelle, never()).resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void doitRefuserSoumission_quandUnTypeDePieceEstManquant() {
        arrangerEtablissementOuvert();

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());

        assertThatThrownBy(() -> service.soumettrePublique("ecole", donneesValides(), null, List.of(piece), List.of(), "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_PIECE_TYPE_MANQUANT));
        // I4 (2e revue) : refus avant toute recherche de doublon, dossier existant ou non.
        verify(insertionTransactionnelle, never()).resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void doitRefuserSoumission_quandTropDePieces() {
        arrangerEtablissementOuvert();
        proprietes.setNombreMaxPieces(1);

        MockMultipartFile acte = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        MockMultipartFile bulletin = new MockMultipartFile("pieces", "bulletin.pdf", "application/pdf", pdfMinimal());

        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(acte, bulletin), List.of("ACTE_NAISSANCE", "BULLETIN"), "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_TROP_DE_PIECES));
        // I4 (2e revue) : refus avant toute recherche de doublon, dossier existant ou non.
        verify(insertionTransactionnelle, never()).resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // 3e revue, point 5 : même fichier joint deux fois -> refus explicite (422)
    // ------------------------------------------------------------------

    @Test
    void doitRefuserSoumission_quandLaMemePieceEstJointeDeuxFoisDansLeMemeEnvoi() {
        arrangerEtablissementOuvert();

        MockMultipartFile acte = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        MockMultipartFile memeActeEncore = new MockMultipartFile("pieces", "acte-encore.pdf", "application/pdf", pdfMinimal());

        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(acte, memeActeEncore), List.of("ACTE_NAISSANCE", "AUTRE"), "41.207.0.1"))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_PIECE_DUPLIQUEE));
        verify(insertionTransactionnelle, never()).resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void doitRefuserAjoutDePiece_quandLaMemeEmpreinteEstDejaAttacheeAuDossier() {
        UUID demandeId = UUID.randomUUID();
        DemandeAdmission existante = new DemandeAdmission(
                etablissementId, "PRE-2026-000099", "CS-000099", anneeId, niveauId, null, "Kodjo", "Ama",
                LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null, "Kodjo", "Père", LienResponsable.PERE,
                "+22890000001", "responsable@example.com", CanalAdmission.PUBLIC, Instant.now(), Instant.now(), "hash");
        when(demandeAdmissionRepository.findById(demandeId)).thenReturn(Optional.of(existante));
        when(stockagePiecesJointes.compterActives(demandeId)).thenReturn(0L);
        when(stockagePiecesJointes.existeDejaPourDemande(eq(demandeId), any())).thenReturn(true);

        MockMultipartFile acte = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());

        assertThatThrownBy(() -> service.ajouterPiece(demandeId, "ACTE_NAISSANCE", acte))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_PIECE_DUPLIQUEE));
    }

    @Test
    void doitRefuserPiece_quandFormatNonSupporte_detectionParMagicBytesPasParExtension() {
        arrangerEtablissementOuvert();

        // Exécutable renommé en .pdf : l'extension et le Content-Type déclarés mentent tous les deux.
        byte[] executable = new byte[]{0x4D, 0x5A, 0x00, 0x01, 0x02};
        MockMultipartFile pieceMalicieuse = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", executable);

        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(pieceMalicieuse), List.of("ACTE_NAISSANCE"), "41.207.0.1"))
                .isInstanceOf(tg.novadigital.edukeys.common.exception.FormatFichierNonSupporteException.class);
        // I4 (2e revue) : refus avant toute recherche de doublon, dossier existant ou non.
        verify(insertionTransactionnelle, never()).resoudreOffreEtVerifierDoublon(any(), any(), any(), any(), any(), any());
    }

    @Test
    void doitRefuserPiece_quandPdfContientDuJavaScript() {
        arrangerEtablissementOuvert();

        byte[] pdfSuspect = ("%PDF-1.4\n1 0 obj << /S /JavaScript /JS (app.alert('x')) >>\n%%EOF")
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfSuspect);

        assertThatThrownBy(() -> service.soumettrePublique(
                "ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1"))
                .isInstanceOf(tg.novadigital.edukeys.common.exception.FormatFichierNonSupporteException.class)
                .satisfies(e -> assertThat(((tg.novadigital.edukeys.common.exception.FormatFichierNonSupporteException) e).getCode())
                        .isEqualTo(CodeErreur.ADMISSION_PIECE_CONTENU_SUSPECT));
    }

    // ------------------------------------------------------------------
    // IP (critère 9) — I7 : HMAC-SHA256, jamais un simple sel SHA-256
    // ------------------------------------------------------------------

    @Test
    void doitHacherLipEnSoixanteQuatreCaracteresHexadecimaux_sansJamaisStockerLipEnClair() {
        arrangerEtablissementOuvert();
        arrangerAucunDoublon();
        DemandeAdmission sauvegardee = new DemandeAdmission(
                etablissementId, "PRE-2026-000002", "CS-000002", anneeId, niveauId, null, "Kodjo", "Ama",
                LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null, "Kodjo", "Père", LienResponsable.PERE,
                "+22890000001", "responsable@example.com", CanalAdmission.PUBLIC, Instant.now(), Instant.now(), "hash");
        ArgumentCaptor<String> ipHashCaptor = ArgumentCaptor.forClass(String.class);
        when(insertionTransactionnelle.inserer(any(), any(), any(), any(), any(), any(), any(), any(), ipHashCaptor.capture(), any(), any()))
                .thenReturn(sauvegardee);

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        service.soumettrePublique("ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1");

        String hash = ipHashCaptor.getValue();
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).doesNotContain("41.207.0.1");
    }

    @Test
    void doitProduireDesHachesDifferents_pourDeuxClesHmacDifferentes() {
        arrangerEtablissementOuvert();
        arrangerAucunDoublon();
        DemandeAdmission sauvegardee = new DemandeAdmission(
                etablissementId, "PRE-2026-000003", "CS-000003", anneeId, niveauId, null, "Kodjo", "Ama",
                LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null, "Kodjo", "Père", LienResponsable.PERE,
                "+22890000001", "responsable@example.com", CanalAdmission.PUBLIC, Instant.now(), Instant.now(), "hash");
        ArgumentCaptor<String> ipHashCaptor = ArgumentCaptor.forClass(String.class);
        when(insertionTransactionnelle.inserer(any(), any(), any(), any(), any(), any(), any(), any(), ipHashCaptor.capture(), any(), any()))
                .thenReturn(sauvegardee);

        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        service.soumettrePublique("ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1");
        String hashPremiereCle = ipHashCaptor.getValue();

        proprietes.setSelHachageIp("autre-cle-hmac-differente");
        service.soumettrePublique("ecole", donneesValides(), null, List.of(piece), List.of("ACTE_NAISSANCE"), "41.207.0.1");
        String hashSecondeCle = ipHashCaptor.getValue();

        assertThat(hashPremiereCle).isNotEqualTo(hashSecondeCle);
    }

    private static byte[] pdfMinimal() {
        return "%PDF-1.4\n1 0 obj <<>>\nendobj\n%%EOF".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    // ------------------------------------------------------------------
    // Décisions (US-07)
    // ------------------------------------------------------------------

    @AfterEach
    void apresChaqueTest() {
        SecurityContextHolder.clearContext();
    }

    private final UUID decideurId = UUID.randomUUID();

    private void arrangerUtilisateurAuthentifie() {
        PrincipalAuditable principal = () -> decideurId.toString();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    private DemandeAdmission demandeEnAttente(UUID id) {
        DemandeAdmission demande = new DemandeAdmission(
                etablissementId, "PRE-2026-000123", "CS-000123", anneeId, niveauId, null, "Kodjo", "Ama",
                LocalDate.of(2015, 5, 12), "Lomé", "F", "TG", null, "Kodjo", "Père", LienResponsable.PERE,
                "+22890000001", "responsable@example.com", CanalAdmission.PUBLIC, Instant.now(), Instant.now(), "hash");
        when(demandeAdmissionRepository.findById(id)).thenReturn(Optional.of(demande));
        return demande;
    }

    @Test
    void doitAccepterUnDossier_sansObservationObligatoire() {
        arrangerUtilisateurAuthentifie();
        UUID demandeId = UUID.randomUUID();
        DemandeAdmission demande = demandeEnAttente(demandeId);

        DemandeAdmission resultat = service.decider(demandeId, StatutDecisionAdmission.ACCEPTEE, null, 0L);

        assertThat(resultat.getStatut()).isEqualTo(StatutAdmission.ACCEPTEE);
        verify(demandeAdmissionRepository).save(demande);
        ArgumentCaptor<DecisionAdmission> decisionCaptor = ArgumentCaptor.forClass(DecisionAdmission.class);
        verify(decisionAdmissionRepository).save(decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().getStatutNouveau()).isEqualTo(StatutAdmission.ACCEPTEE);
        assertThat(decisionCaptor.getValue().getStatutPrecedent()).isEqualTo(StatutAdmission.EN_ATTENTE);
        assertThat(decisionCaptor.getValue().getDecidePar()).isEqualTo(decideurId);
        verify(eventPublisher).publishEvent(any(DecisionAdmissionPriseEvent.class));
    }

    @Test
    void doitRefuserDecision_refus_quandObservationAbsente() {
        arrangerUtilisateurAuthentifie();
        UUID demandeId = UUID.randomUUID();
        demandeEnAttente(demandeId);

        assertThatThrownBy(() -> service.decider(demandeId, StatutDecisionAdmission.REFUSEE, "   ", 0L))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_OBSERVATION_OBLIGATOIRE));
        verify(decisionAdmissionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doitRefuserDecision_listeAttente_quandObservationAbsente() {
        arrangerUtilisateurAuthentifie();
        UUID demandeId = UUID.randomUUID();
        demandeEnAttente(demandeId);

        assertThatThrownBy(() -> service.decider(demandeId, StatutDecisionAdmission.LISTE_ATTENTE, null, 0L))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_OBSERVATION_OBLIGATOIRE));
    }

    @Test
    void doitAccepterRefus_avecObservation() {
        arrangerUtilisateurAuthentifie();
        UUID demandeId = UUID.randomUUID();
        demandeEnAttente(demandeId);

        DemandeAdmission resultat = service.decider(demandeId, StatutDecisionAdmission.REFUSEE, "Dossier incomplet", 0L);

        assertThat(resultat.getStatut()).isEqualTo(StatutAdmission.REFUSEE);
        assertThat(resultat.getMotifDecision()).isEqualTo("Dossier incomplet");
    }

    @Test
    void doitRejeterDecision_quandVersionPerimee_sansAucuneEcriture() {
        arrangerUtilisateurAuthentifie();
        UUID demandeId = UUID.randomUUID();
        demandeEnAttente(demandeId);

        assertThatThrownBy(() -> service.decider(demandeId, StatutDecisionAdmission.ACCEPTEE, null, 99L))
                .isInstanceOf(ConflitException.class)
                .satisfies(e -> assertThat(((ConflitException) e).getCode()).isEqualTo(CodeErreur.ADMISSION_MODIFICATION_CONCURRENTE));
        verify(demandeAdmissionRepository, never()).save(any());
        verify(decisionAdmissionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doitRejeterDecision_quandTransitionInvalide() {
        arrangerUtilisateurAuthentifie();
        UUID demandeId = UUID.randomUUID();
        DemandeAdmission demande = demandeEnAttente(demandeId);
        demande.changerStatut(StatutAdmission.ACCEPTEE, "motif", UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> service.decider(demandeId, StatutDecisionAdmission.REFUSEE, "motif", 0L))
                .isInstanceOf(RegleMetierViolee.class)
                .satisfies(e -> assertThat(((RegleMetierViolee) e).getCode()).isEqualTo(CodeErreur.ADMISSION_TRANSITION_INVALIDE));
        verify(decisionAdmissionRepository, never()).save(any());
    }

    @Test
    void doitTraduireLaViolationDeLindexDeDoublon_enConflitExplicite() {
        arrangerUtilisateurAuthentifie();
        UUID demandeId = UUID.randomUUID();
        demandeEnAttente(demandeId);
        // REFUSEE -> LISTE_ATTENTE peut entrer en collision avec une nouvelle soumission active du même enfant.
        doThrowSurFlush("uk_demandes_admission_doublon");

        assertThatThrownBy(() -> service.decider(demandeId, StatutDecisionAdmission.LISTE_ATTENTE, "Réexamen", 0L))
                .isInstanceOf(ConflitException.class)
                .satisfies(e -> assertThat(((ConflitException) e).getCode()).isEqualTo(CodeErreur.ADMISSION_DOUBLON));
    }

    private void doThrowSurFlush(String contrainte) {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("violation simulée",
                        new ConstraintViolationException("violation simulée", new SQLException("duplicate key", "23505"), contrainte)))
                .when(entityManager).flush();
    }
}
