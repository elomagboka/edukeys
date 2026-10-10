package tg.novadigital.edukeys.admission.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.admission.domain.ContenuPieceJointeAdmission;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Test d'intégration de la soumission publique de pré-inscription (US-06) :
 * MockMvc + Testcontainers PostgreSQL, rollback transactionnel. Couvre en
 * priorité l'idempotence (critère 1, le cas central), puis Turnstile, le
 * champ piège, l'isolation multi-établissement, les permissions et le hachage
 * de l'IP.
 *
 * <p>Le bean {@link tg.novadigital.edukeys.admission.service.VerificationTurnstileService}
 * est remplacé par {@link VerificationTurnstileServiceDouble} (voir
 * {@link ConfigurationTurnstileDoubleTest}) : aucun serveur HTTP simulé, la
 * vérification anti-robot reste active et refusante par défaut partout
 * ailleurs dans l'application.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationTurnstileDoubleTest.class)
@Transactional
@org.springframework.test.context.event.RecordApplicationEvents
class SoumissionPubliqueAdmissionIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private org.springframework.test.context.event.ApplicationEvents evenements;

    @BeforeEach
    void reinitialiserLeDouble() {
        VerificationTurnstileServiceDouble.reinitialiser();
    }

    @AfterEach
    void nettoyerLeDouble() {
        VerificationTurnstileServiceDouble.reinitialiser();
    }

    // ------------------------------------------------------------------
    // 3e revue, point 1 : opacité du code de suivi
    // ------------------------------------------------------------------

    @Test
    void laReponsePublique_neContientJamaisLaReferenceSequentielle_etLeCodeSuiviEstIdempotent() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("OPAQ");

        String reponse1 = soumettre(ctx, "Ama", "Kodjo", "2015-04-04", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(reponse1).doesNotContain("PRE-");
        String codeSuivi1 = JsonPath.read(reponse1, "$.codeSuivi");

        // Idempotence (règle 3) : même enfant -> même code de suivi.
        String reponse2 = soumettre(ctx, "Ama", "Kodjo", "2015-04-04", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(reponse2).doesNotContain("PRE-");
        String codeSuivi2 = JsonPath.read(reponse2, "$.codeSuivi");
        assertThat(codeSuivi2).isEqualTo(codeSuivi1);

        // Deux enfants différents -> deux codes de suivi sans relation d'ordre
        // déductible : aucun préfixe/suffixe commun de compteur séquentiel.
        String reponse3 = soumettre(ctx, "Kossi", "Sena", "2016-06-06", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String codeSuivi3 = JsonPath.read(reponse3, "$.codeSuivi");
        assertThat(codeSuivi3).isNotEqualTo(codeSuivi1);
        assertThat(codeSuivi1).hasSameSizeAs(codeSuivi3);

        // La référence séquentielle interne, elle, reste bien "PRE-...".
        String referenceInterne = jdbcTemplate.queryForObject(
                "select reference from demandes_admission where code_suivi = ?", String.class, codeSuivi1);
        assertThat(referenceInterne).startsWith("PRE-");
    }

    /**
     * US-08, Q5 + Q-D : l'enfant d'un dossier ACCEPTEE reste « vivant » — une re-soumission publique renvoie la même
     * réponse (code de suivi identique), ne crée aucun nouveau dossier et n'émet AUCUN accusé de réception.
     */
    @Test
    void laResoumissionDUnEnfantAccepte_renvoieLaMemeReponse_sansNouveauDossier_niAccuse() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("ACC");
        String reponse1 = soumettre(ctx, "Accepte", "Enfant", "2015-09-19", "jeton-valide", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String codeSuivi = JsonPath.read(reponse1, "$.codeSuivi");
        String idDossier = jdbcTemplate.queryForObject("select id::text from demandes_admission where code_suivi = ?", String.class, codeSuivi);
        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"ACCEPTEE\",\"observation\":null,\"version\":0}"))
                .andExpect(status().isOk());
        evenements.clear();

        String reponse2 = soumettre(ctx, "Accepte", "Enfant", "2015-09-19", "jeton-valide", null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(reponse2, "$.codeSuivi")).isEqualTo(codeSuivi);
        Long dossiers = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, ctx.etablissementId());
        assertThat(dossiers).isEqualTo(1L);
        assertThat(evenements.stream(tg.novadigital.edukeys.admission.service.DemandeAdmissionSoumiseEvent.class).count())
                .as("aucun accusé de réception pour un dossier accepté").isZero();
    }

    // ------------------------------------------------------------------
    // Idempotence (critère 1, le cas central)
    // ------------------------------------------------------------------

    @Test
    void doitRenvoyer201EtLaReferenceExistante_quandLaMemeDemandeEstSoumiseDeuxFoisEnAttente() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("IDEM");

        String reponse1 = soumettre(ctx, "Kodjo", "Ama", "2015-05-12", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference1 = JsonPath.read(reponse1, "$.codeSuivi");

        // I4 : réponse strictement identique (toujours 201, référence + message
        // générique) que le dossier soit nouveau ou déjà existant.
        String reponse2 = soumettre(ctx, "Kodjo", "Ama", "2015-05-12", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference2 = JsonPath.read(reponse2, "$.codeSuivi");

        assertThat(reference2).isEqualTo(reference1);
        Long nombreDeDossiers = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, ctx.etablissementId);
        assertThat(nombreDeDossiers).isEqualTo(1L);
    }

    /**
     * I4 (2e revue) : un tiers qui connaît nom, prénoms et date de naissance
     * envoyait le formulaire SANS pièce. Réponse 201 si l'enfant avait déjà un
     * dossier (le chemin idempotent sautait la validation des pièces), 422
     * sinon : il apprenait ainsi qu'un enfant avait postulé. La réponse doit
     * être la même dans les deux cas.
     */
    @Test
    void refuseUnEnvoiSansPiece_quelEnfantAitDejaUnDossierOuNon() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("SONDE");
        soumettre(ctx, "Kodjo", "Ama", "2015-05-12", "jeton-valide", null).andExpect(status().isCreated());

        String reponseEnfantConnu = soumettreSansPiece(ctx, "Kodjo", "Ama", "2015-05-12")
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        String reponseEnfantInconnu = soumettreSansPiece(ctx, "Mensah", "Kossi", "2014-03-02")
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(reponseEnfantConnu, "$.code"))
                .isEqualTo(JsonPath.read(reponseEnfantInconnu, "$.code"));
    }

    @Test
    void doitRenvoyer201EtLaReferenceExistante_quandLeDossierExistantEstEnListeAttente() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("IDLA");

        String reponse1 = soumettre(ctx, "Afi", "Mensah", "2015-03-01", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference1 = JsonPath.read(reponse1, "$.codeSuivi");
        entityManager.flush();
        jdbcTemplate.update("update demandes_admission set statut = 'LISTE_ATTENTE' where code_suivi = ?", reference1);

        String reponse2 = soumettre(ctx, "Afi", "Mensah", "2015-03-01", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(reponse2, "$.codeSuivi")).isEqualTo(reference1);
        Long nombreDeDossiers = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, ctx.etablissementId);
        assertThat(nombreDeDossiers).isEqualTo(1L);
    }

    @Test
    void neBloquePasUneNouvelleDemande_quandLeDossierExistantEstRefuse() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("REFOK");

        String reponse1 = soumettre(ctx, "Yawa", "Adjo", "2015-08-20", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference1 = JsonPath.read(reponse1, "$.codeSuivi");
        jdbcTemplate.update("update demandes_admission set statut = 'REFUSEE' where code_suivi = ?", reference1);

        String reponse2 = soumettre(ctx, "Yawa", "Adjo", "2015-08-20", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference2 = JsonPath.read(reponse2, "$.codeSuivi");

        assertThat(reference2).isNotEqualTo(reference1);
        Long nombreDeDossiers = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, ctx.etablissementId);
        assertThat(nombreDeDossiers).isEqualTo(2L);
    }

    /** US-08, Q5 (inversion décidée par le PO) : un dossier ACCEPTEE reste « vivant », il BLOQUE désormais un doublon. */
    @Test
    void neCreePasUnNouveauDossier_quandLeDossierExistantEstAccepte() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("ACCOK");

        String reponse1 = soumettre(ctx, "Essi", "Bena", "2015-11-02", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference1 = JsonPath.read(reponse1, "$.codeSuivi");
        jdbcTemplate.update("update demandes_admission set statut = 'ACCEPTEE' where code_suivi = ?", reference1);

        String reponse2 = soumettre(ctx, "Essi", "Bena", "2015-11-02", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(reponse2, "$.codeSuivi")).isEqualTo(reference1);
        Long nombreDeDossiers = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, ctx.etablissementId);
        assertThat(nombreDeDossiers).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Règle 12 CLAUDE.md : le dossier créé est bien relisible après flush
    // ------------------------------------------------------------------

    @Test
    void leDossierCreeEstRelisibleEnBase_apresLaSoumission() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("FLUSH");
        String reponse = soumettre(ctx, "Koffi", "Sena", "2015-01-01", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String codeSuivi = JsonPath.read(reponse, "$.codeSuivi");
        // La référence séquentielle reste interne (3e revue, point 1) : on la relit
        // en base par le code de suivi pour vérifier qu'elle apparaît bien côté admin.
        String reference = jdbcTemplate.queryForObject(
                "select reference from demandes_admission where code_suivi = ?", String.class, codeSuivi);

        String jetonAdmin = ctx.jetonAdmin;
        mockMvc.perform(get("/api/v1/demandes-admission")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(reference));
    }

    // ------------------------------------------------------------------
    // Turnstile (règle 4, critère 2)
    // ------------------------------------------------------------------

    @Test
    void doitRefuser_quandJetonTurnstileAbsent() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("TSABS");
        soumettre(ctx, "Ama", "Efo", "2015-01-01", null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_CAPTCHA_ECHEC"));
    }

    @Test
    void doitRefuser_quandJetonTurnstileInvalide() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("TSINV");
        VerificationTurnstileServiceDouble.definirMode(VerificationTurnstileServiceDouble.Mode.ECHEC);
        soumettre(ctx, "Ama", "Efo", "2015-01-01", "jeton-invalide", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_CAPTCHA_ECHEC"));
    }

    @Test
    void doitRefuser_quandLeServiceTurnstileEstIndisponible_jamaisUnPassageSilencieux() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("TSIND");
        VerificationTurnstileServiceDouble.definirMode(VerificationTurnstileServiceDouble.Mode.SERVICE_INDISPONIBLE);
        soumettre(ctx, "Ama", "Efo", "2015-01-01", "jeton-quelconque", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_CAPTCHA_ECHEC"));
    }

    /** Turnstile doit être vérifié avant tout traitement des fichiers : une pièce malformée ne doit pas masquer le refus Turnstile. */
    @Test
    void refuseSurTurnstileAvantMemeDexaminerLesFichiers() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("TSORD");
        VerificationTurnstileServiceDouble.definirMode(VerificationTurnstileServiceDouble.Mode.ECHEC);

        MockMultipartFile demandePart = construireDemandeJson(ctx, "Ama", "Efo", "2015-01-01", null);
        MockMultipartFile pieceCorrompue = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", new byte[0]);

        tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code + "/demandes-admission")
                        .file(demandePart).file(pieceCorrompue).param("typesPieces", "ACTE_NAISSANCE")
                        .header("CF-Turnstile-Response", "jeton-invalide"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_CAPTCHA_ECHEC"));
    }

    // ------------------------------------------------------------------
    // Honeypot (règle 6, critère 8)
    // ------------------------------------------------------------------

    @Test
    void renvoieUneReferenceFictive_quandLeChampPiegeEstRempli_sansRienEnregistrer() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("HONEY");

        String reponse = soumettre(ctx, "Robot", "Malicieux", "2015-01-01", "jeton-valide", "http://spam.example")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(reponse, "$.codeSuivi")).isEqualTo("0".repeat(26));
        Long nombreDeDossiers = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, ctx.etablissementId);
        assertThat(nombreDeDossiers).isEqualTo(0L);
    }

    // ------------------------------------------------------------------
    // Isolation multi-établissement (règle 11 CLAUDE.md) et permissions
    // ------------------------------------------------------------------

    @Test
    void unDossierDunEtablissement_nestNiListeNiLisibleDepuisUnAutreEtablissement() throws Exception {
        Contexte ctxA = preparerEtablissementEtOffre("ISOA");
        Contexte ctxB = preparerEtablissementEtOffre("ISOB");

        String reponse = soumettre(ctxA, "Sena", "Kokou", "2015-06-06", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idDossier = trouverIdParCodeSuivi(JsonPath.read(reponse, "$.codeSuivi"));

        mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier)
                        .header("Authorization", "Bearer " + ctxB.jetonAdmin))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/demandes-admission")
                        .header("Authorization", "Bearer " + ctxB.jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void refuse403_unSuperAdmin_surLesDemandesAdmission() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        mockMvc.perform(get("/api/v1/demandes-admission")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse403_unUtilisateurSansPermission_surLesEndpointsAuthentifies() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("PRM");
        String jetonSansPermission = creerUtilisateurSansRoleEtObtenirToken(ctx.etablissementId);

        mockMvc.perform(get("/api/v1/demandes-admission")
                        .header("Authorization", "Bearer " + jetonSansPermission))
                .andExpect(status().isForbidden());
    }

    @Test
    void lesDeuxRoutesPubliques_repondentSansAuthentification() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("PUB");

        mockMvc.perform(get("/api/v1/public/etablissements/" + ctx.code + "/offre-admission"))
                .andExpect(status().isOk());

        soumettre(ctx, "Sans", "Auth", "2015-09-09", "jeton-valide", null)
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Hachage de l'IP (règle 4 spec, critère 9)
    // ------------------------------------------------------------------

    @Test
    void hacheLipEnSoixanteQuatreHexadecimaux_sansStockerLipEnClair() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("IPHA");
        String reponse = soumettre(ctx, "Ip", "Test", "2015-01-01", "jeton-valide", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(reponse, "$.codeSuivi");

        String hash = jdbcTemplate.queryForObject(
                "select ip_soumission_hash from demandes_admission where code_suivi = ?", String.class, reference);
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        // 127.0.0.1 est l'adresse distante par défaut de MockMvc : elle ne doit apparaître nulle part.
        assertThat(hash).doesNotContain("127001").doesNotContain("127.0.0.1");
    }

    // ------------------------------------------------------------------
    // Téléchargement d'une pièce (critère 10)
    // ------------------------------------------------------------------

    @Test
    void telechargementDunePiece_porteAttachmentEtNosniff_jamaisInline() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DL");
        MockMultipartFile demandePart = construireDemandeJson(ctx, "Dl", "Test", "2015-02-02", null);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());

        String reponse = tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code + "/demandes-admission")
                        .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                        .header("CF-Turnstile-Response", "jeton-valide"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(reponse, "$.codeSuivi");
        String idDossier = trouverIdParCodeSuivi(reference);

        String reponseDetail = mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier)
                        .header("Authorization", "Bearer " + ctx.jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String pieceId = JsonPath.read(reponseDetail, "$.pieces[0].id");

        mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier + "/pieces/" + pieceId)
                        .header("Authorization", "Bearer " + ctx.jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Content-Type-Options", "nosniff"));
    }

    // ------------------------------------------------------------------
    // B3 (revue) : le contenu binaire n'est jamais chargé au détail d'un dossier
    // ------------------------------------------------------------------

    @Test
    void leDetailDunDossier_neChargeJamaisLeContenuDesPieces() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("B3");
        MockMultipartFile demandePart = construireDemandeJson(ctx, "Contenu", "Test", "2015-02-02", null);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());

        String reponse = tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code + "/demandes-admission")
                        .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                        .header("CF-Turnstile-Response", "jeton-valide"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(reponse, "$.codeSuivi");
        String idDossier = trouverIdParCodeSuivi(reference);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier)
                        .header("Authorization", "Bearer " + ctx.jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pieces.length()").value(1));

        assertThat(stats.getEntityStatistics(ContenuPieceJointeAdmission.class.getName()).getLoadCount())
                .withFailMessage("Le détail d'un dossier ne doit jamais charger ContenuPieceJointeAdmission (B3, revue).")
                .isZero();
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private record Contexte(String etablissementId, String code, String jetonAdmin, String niveauId) {
    }

    /** B2+I1 : le jeton Turnstile voyage désormais dans l'en-tête {@code CF-Turnstile-Response}, plus dans le JSON. */
    private org.springframework.test.web.servlet.ResultActions soumettre(
            Contexte ctx, String nom, String prenoms, String dateNaissance, String turnstileToken, String honeypot) throws Exception {
        MockMultipartFile demandePart = construireDemandeJson(ctx, nom, prenoms, dateNaissance, honeypot);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());

        var requete = multipart("/api/v1/public/etablissements/" + ctx.code + "/demandes-admission")
                .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE");
        if (turnstileToken != null) {
            requete.header("CF-Turnstile-Response", turnstileToken);
        }
        return tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, requete);
    }

    private org.springframework.test.web.servlet.ResultActions soumettreSansPiece(
            Contexte ctx, String nom, String prenoms, String dateNaissance) throws Exception {
        return tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code + "/demandes-admission")
                .file(construireDemandeJson(ctx, nom, prenoms, dateNaissance, null))
                .header("CF-Turnstile-Response", "jeton-valide"));
    }

    private MockMultipartFile construireDemandeJson(
            Contexte ctx, String nom, String prenoms, String dateNaissance, String honeypot) {
        String honeypotJson = honeypot == null ? "null" : "\"" + honeypot + "\"";
        String json = """
                {
                  "demande": {
                    "niveauId": "%s",
                    "nom": "%s",
                    "prenoms": "%s",
                    "dateNaissance": "%s",
                    "lieuNaissance": "Lomé",
                    "sexe": "F",
                    "nationalite": "TG",
                    "responsableNom": "Responsable",
                    "responsablePrenoms": "Test",
                    "responsableLien": "PERE",
                    "responsableTelephone": "+22890000000",
                    "responsableEmail": "responsable@example.com",
                    "consentementDonnees": true
                  },
                  "siteWeb": %s
                }
                """.formatted(ctx.niveauId, nom, prenoms, dateNaissance, honeypotJson);
        return new MockMultipartFile("demande", "demande.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] pdfMinimal() {
        return "%PDF-1.4\n1 0 obj <<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    }

    private String trouverIdParCodeSuivi(String reference) {
        return jdbcTemplate.queryForObject("select id from demandes_admission where code_suivi = ?", String.class, reference);
    }

    private Contexte preparerEtablissementEtOffre(String prefixeCode) throws Exception {
        String etablissementId = creerEtablissement(prefixeCode);
        String code = jdbcTemplate.queryForObject("select code from etablissements where id = ?::uuid", String.class, etablissementId);
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        creerEtActiverAnneeScolaire(jetonAdmin);
        String cycleId = creerCycle(jetonAdmin, "Collège", prefixeCode + "C", 1);
        String niveauId = creerNiveau(jetonAdmin, "6ème", prefixeCode + "N", 1, cycleId);
        jdbcTemplate.update("update etablissements set admissions_ouvertes = true where id = ?::uuid", etablissementId);
        // La mise à jour ci-dessus passe par JDBC direct : elle contourne le cache de premier niveau
        // d'Hibernate, qui garde encore l'entité Etablissement chargée par les appels précédents.
        entityManager.flush();
        entityManager.clear();
        return new Contexte(etablissementId, code, jetonAdmin, niveauId);
    }

    private String creerCycle(String jetonAdmin, String libelle, String code, int rang) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s","rang":%d}
                                """.formatted(libelle, code, rang)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private String creerNiveau(String jetonAdmin, String libelle, String code, int rang, String cycleId) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/niveaux")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s","rang":%d,"cycleId":"%s"}
                                """.formatted(libelle, code, rang, cycleId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private void creerEtActiverAnneeScolaire(String jetonAdmin) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-01","dateFin":"2027-07-15"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(reponse, "$.id");
        mockMvc.perform(post("/api/v1/annees-scolaires/" + id + "/activation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());
    }

    private String creerEtablissement(String prefixeCode) throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = prefixeCode + System.nanoTime() % 100000;

        String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement %s","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg",
                                 "emailAdministrateur":"admin.%s@edukeys.tg","nomCompletAdministrateur":"Admin Test"}
                                """.formatted(code, code, code.toLowerCase(), code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponseEtab, "$.etablissement.id");
    }

    private String creerAdminEtObtenirToken(String etablissementId) throws Exception {
        tg.novadigital.edukeys.identite.domain.Utilisateur compteAdmin = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "admin.us06." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Admin US-06 Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        return connecterEtObtenirAccessToken(compteAdmin.getEmail());
    }

    private String creerUtilisateurSansRoleEtObtenirToken(String etablissementId) throws Exception {
        tg.novadigital.edukeys.identite.domain.Utilisateur compte = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "sanspermission.us06." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Sans Permission Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), etablissementId);

        return connecterEtObtenirAccessToken(compte.getEmail());
    }

    private String connecterEtObtenirAccessToken(String email) throws Exception {
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifiant":"%s","motDePasse":"%s"}
                                """.formatted(email, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(reponseLogin, "$.accessToken");
    }
}
