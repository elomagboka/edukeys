package tg.novadigital.edukeys.admission.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration US-07 : décision sur un dossier d'admission (endpoint
 * {@code POST /api/v1/demandes-admission/{id}/decisions}), machine à états,
 * observation obligatoire, version optimiste, isolation multi-établissement,
 * permissions.
 *
 * <p>Rollback transactionnel (CLAUDE.md, règle 4) : la notification
 * après-commit (critère « notification automatique ») est couverte à part,
 * dans {@link DecisionAdmissionNotificationIntegrationTest}, puisque {@code
 * @TransactionalEventListener(AFTER_COMMIT)} ne se déclenche jamais dans un
 * test qui n'arrive jamais à committer. De même, l'historisation Envers (qui
 * n'écrit qu'au commit) et la collision {@code uk_demandes_admission_doublon}
 * (dont la violation, non rattrapée par une lecture ultérieure dans la même
 * transaction, empoisonnerait la transaction du test — 25P02) sont couvertes
 * dans {@link DecisionAdmissionEnversEtDoublonIntegrationTest}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(tg.novadigital.edukeys.admission.web.ConfigurationTurnstileDoubleTest.class)
@Transactional
class DecisionAdmissionIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------
    // Endpoint principal
    // ------------------------------------------------------------------

    @Test
    void doitEnregistrerLaDecision_etRenvoyerLeDossierAJour() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC1");
        String idDossier = creerDossierAdmin(ctx, "Kodjo", "Ama", "2015-05-12", "+22890001001");

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDecision("REFUSEE", "Dossier incomplet", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE"))
                .andExpect(jsonPath("$.observationDecision").value("Dossier incomplet"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.decisions[0].statutPrecedent").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.decisions[0].statutNouveau").value("REFUSEE"))
                .andExpect(jsonPath("$.decisions[0].observation").value("Dossier incomplet"));

        mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier)
                        .header("Authorization", "Bearer " + ctx.jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE"))
                .andExpect(jsonPath("$.observationDecision").value("Dossier incomplet"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.decisions.length()").value(1));
    }

    // ------------------------------------------------------------------
    // Machine à états
    // ------------------------------------------------------------------

    @Test
    void doitSuivreLaMachineAEtats_puisRefuserLaTransitionDepuisUnEtatFinal() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC2");
        String idDossier = creerDossierAdmin(ctx, "Yao", "Afi", "2016-02-02", "+22890001002");

        decider(ctx, idDossier, "REFUSEE", "Incomplet", 0).andExpect(status().isOk());
        decider(ctx, idDossier, "LISTE_ATTENTE", "A revoir apres complement", 1).andExpect(status().isOk());
        decider(ctx, idDossier, "ACCEPTEE", null, 2).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/demandes-admission/" + idDossier)
                        .header("Authorization", "Bearer " + ctx.jetonAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.decisions.length()").value(3))
                .andExpect(jsonPath("$.decisions[0].statutPrecedent").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.decisions[0].statutNouveau").value("REFUSEE"))
                .andExpect(jsonPath("$.decisions[1].statutPrecedent").value("REFUSEE"))
                .andExpect(jsonPath("$.decisions[1].statutNouveau").value("LISTE_ATTENTE"))
                .andExpect(jsonPath("$.decisions[2].statutPrecedent").value("LISTE_ATTENTE"))
                .andExpect(jsonPath("$.decisions[2].statutNouveau").value("ACCEPTEE"));

        // ACCEPTEE est un état final : toute nouvelle décision est refusée.
        decider(ctx, idDossier, "REFUSEE", "Trop tard", 3)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_TRANSITION_INVALIDE"));
    }

    // ------------------------------------------------------------------
    // Observation obligatoire / limite de taille
    // ------------------------------------------------------------------

    @Test
    void doitRejeterLaDecision_quandObservationManquantePourUnRefus() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC3");
        String idDossier = creerDossierAdmin(ctx, "Ayele", "Koffi", "2015-03-03", "+22890001003");

        decider(ctx, idDossier, "REFUSEE", null, 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_OBSERVATION_OBLIGATOIRE"));
    }

    @Test
    void doitRejeterLaDecision_quandObservationManquantePourUneListeAttente() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC4");
        String idDossier = creerDossierAdmin(ctx, "Sena", "Mawuli", "2015-04-04", "+22890001004");

        decider(ctx, idDossier, "LISTE_ATTENTE", "   ", 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_OBSERVATION_OBLIGATOIRE"));
    }

    @Test
    void doitAccepterUnRefus_sansObservation_quandLaCibleEstAcceptee() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC5");
        String idDossier = creerDossierAdmin(ctx, "Edem", "Fafa", "2015-06-06", "+22890001005");

        decider(ctx, idDossier, "ACCEPTEE", null, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observationDecision").doesNotExist());
    }

    @Test
    void doitRejeter400_quandObservationDepasseLaTailleMaximale() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC6");
        String idDossier = creerDossierAdmin(ctx, "Adzo", "Mensah", "2015-07-07", "+22890001006");

        String observationTropLongue = "x".repeat(501);
        decider(ctx, idDossier, "REFUSEE", observationTropLongue, 0)
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Version optimiste périmée
    // ------------------------------------------------------------------

    @Test
    void doitRejeter409_quandLaVersionEstPerimee() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC7");
        String idDossier = creerDossierAdmin(ctx, "Kossi", "Abra", "2015-08-08", "+22890001007");

        decider(ctx, idDossier, "REFUSEE", "Version perimee volontairement", 99)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMISSION_MODIFICATION_CONCURRENTE"));

        Long nombreDeDecisions = jdbcTemplate.queryForObject(
                "select count(*) from decisions_admission where demande_id = ?::uuid", Long.class, idDossier);
        assertThat(nombreDeDecisions).isZero();

        String statutActuel = jdbcTemplate.queryForObject(
                "select statut from demandes_admission where id = ?::uuid", String.class, idDossier);
        assertThat(statutActuel).isEqualTo("EN_ATTENTE");
    }

    // ------------------------------------------------------------------
    // Isolation multi-établissement
    // ------------------------------------------------------------------

    @Test
    void doitRejeter404_quandLeDossierAppartientAUnAutreEtablissement() throws Exception {
        Contexte ctxA = preparerEtablissementEtOffre("DEC8A");
        Contexte ctxB = preparerEtablissementEtOffre("DEC8B");
        String idDossierB = creerDossierAdmin(ctxB, "Elom", "Selom", "2015-09-09", "+22890001008");

        decider(ctxA, idDossierB, "REFUSEE", "Incomplet", 0)
                .andExpect(status().isNotFound());

        String statutActuel = jdbcTemplate.queryForObject(
                "select statut from demandes_admission where id = ?::uuid", String.class, idDossierB);
        assertThat(statutActuel).isEqualTo("EN_ATTENTE");
        Long nombreDeDecisions = jdbcTemplate.queryForObject(
                "select count(*) from decisions_admission where demande_id = ?::uuid", Long.class, idDossierB);
        assertThat(nombreDeDecisions).isZero();
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    @Test
    void doitRejeter403_quandLAppelantEstGestionnaire() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC9");
        String idDossier = creerDossierAdmin(ctx, "Dela", "Nuku", "2015-10-10", "+22890001009");
        String jetonGestionnaire = creerUtilisateurEtObtenirToken(ctx.etablissementId(), "GESTIONNAIRE");

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + jetonGestionnaire)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDecision("REFUSEE", "Incomplet", 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void doitRejeter403_quandLAppelantEstDirection() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC10");
        String idDossier = creerDossierAdmin(ctx, "Ama", "Ekoue", "2015-11-11", "+22890001010");
        String jetonDirection = creerUtilisateurEtObtenirToken(ctx.etablissementId(), "DIRECTION");

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + jetonDirection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDecision("REFUSEE", "Incomplet", 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void doitRejeter403_quandLAppelantEstSuperAdmin() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC11");
        String idDossier = creerDossierAdmin(ctx, "Kokou", "Adjovi", "2015-12-12", "+22890001011");
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDecision("REFUSEE", "Incomplet", 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void doitRejeter401_quandLAppelantEstAnonyme() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC12");
        String idDossier = creerDossierAdmin(ctx, "Fifi", "Afiwa", "2016-01-01", "+22890001012");

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsDecision("REFUSEE", "Incomplet", 0)))
                .andExpect(status().isUnauthorized());
    }

    // Historisation Envers et collision uk_demandes_admission_doublon : voir
    // DecisionAdmissionEnversEtDoublonIntegrationTest (correction post-revue).
    // Les deux cas provoquaient soit une lecture après une violation de
    // contrainte non rattrapée (25P02, transaction empoisonnée), soit une
    // lecture avant commit (Envers n'écrit qu'au commit) : incompatibles avec
    // le rollback transactionnel de cette classe.

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private record Contexte(String etablissementId, String code, String jetonAdmin, String niveauId) {
    }

    private ResultActions decider(Contexte ctx, String idDossier, String statut, String observation, long version) throws Exception {
        return mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                .header("Authorization", "Bearer " + ctx.jetonAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsDecision(statut, observation, version)));
    }

    private String corpsDecision(String statut, String observation, long version) {
        String observationJson = observation == null ? "null" : "\"" + observation + "\"";
        return """
                {"statut":"%s","observation":%s,"version":%d}
                """.formatted(statut, observationJson, version);
    }

    private String creerDossierAdmin(Contexte ctx, String nom, String prenoms, String dateNaissance, String telephone) throws Exception {
        MockMultipartFile demandePart = construireDemandeJson(ctx, nom, prenoms, dateNaissance, telephone);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        String reponse = mockMvc.perform(multipart("/api/v1/demandes-admission")
                        .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private MockMultipartFile construireDemandeJson(Contexte ctx, String nom, String prenoms, String dateNaissance, String telephone) {
        String json = """
                {
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
                  "responsableTelephone": "%s",
                  "responsableEmail": "responsable@example.com",
                  "consentementDonnees": true
                }
                """.formatted(ctx.niveauId(), nom, prenoms, dateNaissance, telephone);
        return new MockMultipartFile("demande", "demande.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] pdfMinimal() {
        return "%PDF-1.4\n1 0 obj <<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
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
                        "u.us07dec." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Utilisateur US-07 Décision Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, roleCode);

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
