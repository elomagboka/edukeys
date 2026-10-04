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
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration US-06 : isolation multi-établissement du téléchargement
 * d'une pièce, rôle DIRECTION (lecture seule), absence de N+1 sur la liste
 * des demandes, et limitation de débit sur les routes publiques.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationTurnstileDoubleTest.class)
@Transactional
class AdmissionAccesEtDebitIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private tg.novadigital.edukeys.common.securite.limitation.FiltreLimitationDebit filtreLimitationDebit;

    /**
     * Seuil IP admission abaissé pour ce test uniquement (I3) : la famille
     * admission n'a plus de compteur par compte depuis B1 (plus de lecture du
     * corps multipart) — seul le compteur IP dédié {@code par-ip-admission}
     * est observable ici, avec son seuil de production (500) hors de portée
     * d'un test.
     */
    @org.springframework.test.context.DynamicPropertySource
    static void abaisserLeSeuilParIp(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("edukeys.securite.limitation-debit.par-ip-admission.seuil-tolerance", () -> "3");
        // I4 (3e revue) : plancher de temps de réponse réactivé pour ce test,
        // court pour ne pas alourdir la classe. Le profil test le met à 0.
        registry.add("edukeys.admission.plancher-temps-reponse", () -> PLANCHER_TEST.toMillis() + "ms");
        // Point 3 (3e revue) : budget de succès abaissé, hors de portée du seuil de production (20).
        registry.add("edukeys.securite.limitation-debit.budget-succes-admission-par-jour", () -> String.valueOf(BUDGET_SUCCES_TEST));
    }

    private static final java.time.Duration PLANCHER_TEST = java.time.Duration.ofMillis(400);
    // >= au nombre maximal de soumissions réussies effectuées par un même test de cette classe
    // (listerDemandes_... en effectue 6 dans un seul test), sans quoi ce test isolé ferait
    // échouer des tests sans rapport avec le budget de succès.
    private static final int BUDGET_SUCCES_TEST = 10;

    @BeforeEach
    void reinitialiserLeDouble() {
        VerificationTurnstileServiceDouble.reinitialiser();
        filtreLimitationDebit.reinitialiserPourLesTests();
    }

    // ------------------------------------------------------------------
    // Isolation multi-établissement du téléchargement (règle 11 CLAUDE.md)
    // ------------------------------------------------------------------

    @Test
    void refuse404_telechargementDunePieceDunAutreEtablissement() throws Exception {
        Contexte ctxA = preparerEtablissementEtOffre("DLISOA");
        Contexte ctxB = preparerEtablissementEtOffre("DLISOB");

        String reponse = soumettre(ctxA).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(reponse, "$.codeSuivi");
        String idDossier = trouverIdParCodeSuivi(reference);

        String reponseDetail = mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier)
                        .header("Authorization", "Bearer " + ctxA.jetonAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String pieceId = JsonPath.read(reponseDetail, "$.pieces[0].id");

        mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier + "/pieces/" + pieceId)
                        .header("Authorization", "Bearer " + ctxB.jetonAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse403_superAdmin_surLeTelechargementDunePiece() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DLSA");
        String reponse = soumettre(ctx).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idDossier = trouverIdParCodeSuivi(JsonPath.read(reponse, "$.codeSuivi"));

        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier + "/pieces/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Rôle DIRECTION : consulte, ne crée pas (critère 2)
    // ------------------------------------------------------------------

    @Test
    void directionPeutLister_maisPasCreerUneDemandeAdmission() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DIR");
        String jetonDirection = creerUtilisateurEtObtenirToken(ctx.etablissementId(), "DIRECTION");

        mockMvc.perform(get("/api/v1/demandes-admission")
                        .header("Authorization", "Bearer " + jetonDirection))
                .andExpect(status().isOk());

        MockMultipartFile demandePart = construireDemandeJson(ctx);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        mockMvc.perform(multipart("/api/v1/demandes-admission")
                        .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                        .header("Authorization", "Bearer " + jetonDirection))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse403_utilisateurSansAdmissionCreer_surLaCreationBackOffice() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("NOCREER");
        String jetonSansRole = creerUtilisateurSansRoleEtObtenirToken(ctx.etablissementId());

        MockMultipartFile demandePart = construireDemandeJson(ctx);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        mockMvc.perform(multipart("/api/v1/demandes-admission")
                        .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                        .header("Authorization", "Bearer " + jetonSansRole))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Anti N+1 sur la liste (CLAUDE.md, règle 10)
    // ------------------------------------------------------------------

    @Test
    void listerDemandes_emetLeMemeNombreDeRequetesSql_quelQueSoitLeNombreDeDemandes() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("NPADM");
        for (int i = 0; i < 2; i++) {
            soumettreAvecIdentite(ctx, "Nom" + i, "Prenom" + i, "2015-0" + (i % 9 + 1) + "-01")
                    .andExpect(status().isCreated());
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/demandes-admission")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        long requetesAvecDeuxDemandes = stats.getPrepareStatementCount();

        for (int i = 2; i < 6; i++) {
            soumettreAvecIdentite(ctx, "Nom" + i, "Prenom" + i, "2015-0" + (i % 9 + 1) + "-01")
                    .andExpect(status().isCreated());
        }

        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/demandes-admission")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(6));
        long requetesAvecSixDemandes = stats.getPrepareStatementCount();

        assertThat(requetesAvecSixDemandes)
                .withFailMessage(
                        "Le nombre de requêtes SQL doit être identique quel que soit le nombre de demandes "
                                + "(%d avec 2 demandes, %d avec 6 demandes) : un écart révèle un N+1 sur les libellés niveau/classe.",
                        requetesAvecDeuxDemandes, requetesAvecSixDemandes)
                .isEqualTo(requetesAvecDeuxDemandes);
    }

    // ------------------------------------------------------------------
    // Limitation de débit (règle 5 CLAUDE.md) : 429 indifférencié
    // ------------------------------------------------------------------

    @Test
    void repond429_apresDeNombreusesTentativesEchoueesSurUneMemeCible() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEBIT");
        VerificationTurnstileServiceDouble.definirMode(VerificationTurnstileServiceDouble.Mode.ECHEC);

        boolean recu429 = false;
        for (int i = 0; i < 10 && !recu429; i++) {
            int statut = soumettreAvecIdentite(ctx, "Rate" + i, "Limit" + i, "2015-01-01")
                    .andReturn().getResponse().getStatus();
            if (statut == 429) {
                recu429 = true;
            } else {
                assertThat(statut).isEqualTo(422); // Turnstile en échec : refus métier tant que le seuil n'est pas atteint.
            }
        }
        assertThat(recu429)
                .withFailMessage("Aucune réponse 429 reçue après des tentatives répétées sur la même cible : "
                        + "la limitation de débit ne semble pas active sur la route publique de soumission.")
                .isTrue();
    }

    /**
     * 3e revue, point 3 : le débit ne comptait auparavant que les échecs — un
     * jeton Turnstile valide permettait un dépôt illimité de dossiers, chacun
     * jusqu'à 15 Mo de pièces jointes. Le budget de soumissions RÉUSSIES par
     * IP et par jour ({@code edukeys.securite.limitation-debit.budget-succes-admission-par-jour},
     * abaissé à {@link #BUDGET_SUCCES_TEST} ci-dessous) doit refuser la N+1e.
     */
    @Test
    void repond429_apresLeBudgetDeSoumissionsReussiesParIpEtParJour() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("BUDGET");

        for (int i = 0; i < BUDGET_SUCCES_TEST; i++) {
            soumettreAvecIdentite(ctx, "Budget" + i, "Succes" + i, "2015-01-0" + (i % 9 + 1))
                    .andExpect(status().isCreated());
        }

        int statutApresBudget = soumettreAvecIdentite(ctx, "BudgetDepasse", "Refuse", "2015-02-02")
                .andReturn().getResponse().getStatus();
        assertThat(statutApresBudget)
                .withFailMessage("La soumission suivant l'épuisement du budget de succès journalier doit être refusée en 429.")
                .isEqualTo(429);
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private record Contexte(String etablissementId, String code, String jetonAdmin, String niveauId) {
    }

    /**
     * I4 (3e revue) : la réponse est déjà identique, mais son délai ne l'était
     * pas — un dossier existant revient sans insertion, donc plus vite. Un
     * tiers connaissant nom, prénoms et date de naissance pouvait chronométrer
     * la réponse pour savoir si un enfant a déjà postulé. Le plancher égalise
     * les deux chemins : retirer {@code PlancherTempsReponseAdmission} du
     * contrôleur fait tomber ce test (l'écart redevient visible et la
     * création repasse sous le plancher).
     */
    @Test
    void repondEnUnTempsEgal_queLeDossierSoitNouveauOuDejaExistant() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("TIMING");

        long dureeCreation = chronometrerSoumission(ctx, "Kodjo", "Ama", "2015-05-12");
        long dureeDoublon = chronometrerSoumission(ctx, "Kodjo", "Ama", "2015-05-12");

        // Les deux chemins partent au plus tôt au bout du plancher : la création
        // (insertion du dossier et de la pièce) comme le doublon (simple relecture).
        assertThat(dureeCreation).isGreaterThanOrEqualTo(PLANCHER_TEST.toMillis());
        assertThat(dureeDoublon).isGreaterThanOrEqualTo(PLANCHER_TEST.toMillis());
        // Un seul dossier, malgré les deux envois (idempotence).
        Long dossiers = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, ctx.etablissementId());
        assertThat(dossiers).isEqualTo(1L);
    }

    private long chronometrerSoumission(Contexte ctx, String nom, String prenoms, String dateNaissance) throws Exception {
        long debut = System.nanoTime();
        soumettreAvecIdentite(ctx, nom, prenoms, dateNaissance).andExpect(status().isCreated());
        return java.time.Duration.ofNanos(System.nanoTime() - debut).toMillis();
    }

    private org.springframework.test.web.servlet.ResultActions soumettre(Contexte ctx) throws Exception {
        return soumettreAvecIdentite(ctx, "Nom", "Prenoms", "2015-01-01");
    }

    private org.springframework.test.web.servlet.ResultActions soumettreAvecIdentite(
            Contexte ctx, String nom, String prenoms, String dateNaissance) throws Exception {
        MockMultipartFile demandePart = construireDemandePubliqueJson(ctx, nom, prenoms, dateNaissance);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        // Point 4 (3e revue) : la soumission publique est désormais asynchrone (DeferredResult).
        return tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc,
                multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                        .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                        .header("CF-Turnstile-Response", "jeton-valide"));
    }

    /** Corps attendu par la route publique (B2+I1 : le jeton Turnstile voyage désormais dans l'en-tête, plus dans le JSON). */
    private MockMultipartFile construireDemandePubliqueJson(Contexte ctx, String nom, String prenoms, String dateNaissance) {
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
                  "siteWeb": null
                }
                """.formatted(ctx.niveauId(), nom, prenoms, dateNaissance);
        return new MockMultipartFile("demande", "demande.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    /** Corps attendu par la route back-office : {@code DonneesDemandeAdmissionDto} nu, sans enveloppe ni Turnstile. */
    private MockMultipartFile construireDemandeJson(Contexte ctx) {
        String json = """
                {
                  "niveauId": "%s",
                  "nom": "Nom",
                  "prenoms": "Prenoms",
                  "dateNaissance": "2015-01-01",
                  "lieuNaissance": "Lomé",
                  "sexe": "F",
                  "nationalite": "TG",
                  "responsableNom": "Responsable",
                  "responsablePrenoms": "Test",
                  "responsableLien": "PERE",
                  "responsableTelephone": "+22890000000",
                  "responsableEmail": "responsable@example.com",
                  "consentementDonnees": true
                }
                """.formatted(ctx.niveauId());
        return new MockMultipartFile("demande", "demande.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] pdfMinimal() {
        return "%PDF-1.4\n1 0 obj <<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    }

    private String trouverIdParCodeSuivi(String codeSuivi) {
        return jdbcTemplate.queryForObject("select id from demandes_admission where code_suivi = ?", String.class, codeSuivi);
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
        return creerUtilisateurEtObtenirToken(etablissementId, "ADMIN");
    }

    private String creerUtilisateurEtObtenirToken(String etablissementId, String roleCode) throws Exception {
        tg.novadigital.edukeys.identite.domain.Utilisateur compte = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "u.us06acces." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Utilisateur US-06 Accès Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, roleCode);

        return connecterEtObtenirAccessToken(compte.getEmail());
    }

    private String creerUtilisateurSansRoleEtObtenirToken(String etablissementId) throws Exception {
        tg.novadigital.edukeys.identite.domain.Utilisateur compte = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "sanspermission.us06acces." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Sans Permission Accès Test", false));
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

    private Contexte preparerEtablissementEtOffre(String prefixeCode) throws Exception {
        String etablissementId = creerEtablissement(prefixeCode);
        String code = jdbcTemplate.queryForObject("select code from etablissements where id = ?::uuid", String.class, etablissementId);
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        creerEtActiverAnneeScolaire(jetonAdmin);
        String cycleId = creerCycle(jetonAdmin, "Collège", prefixeCode + "C", 1);
        String niveauId = creerNiveau(jetonAdmin, "6ème", prefixeCode + "N", 1, cycleId);
        jdbcTemplate.update("update etablissements set admissions_ouvertes = true where id = ?::uuid", etablissementId);
        entityManager.flush();
        entityManager.clear();
        return new Contexte(etablissementId, code, jetonAdmin, niveauId);
    }
}
