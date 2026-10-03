package tg.novadigital.edukeys.admission.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.common.notification.Notificateur;
import tg.novadigital.edukeys.common.notification.TypeNotification;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Notification automatique au responsable d'une décision d'admission
 * (US-07), déclenchée {@code AFTER_COMMIT}. Volontairement <strong>pas</strong>
 * {@code @Transactional} (CLAUDE.md, règle 4 : un établissement dédié par
 * test, jamais nettoyé par suppression) — un test à rollback ne committe
 * jamais, et {@code @TransactionalEventListener(AFTER_COMMIT)} ne se
 * déclencherait donc jamais, comme le fait déjà {@code
 * AdmissionIdempotenceConcurrenceIntegrationTest} pour l'accusé de réception
 * (US-06).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationTurnstileDoubleTest.class)
class DecisionAdmissionNotificationIntegrationTest {

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

    @MockitoSpyBean
    private Notificateur notificateur;

    @Test
    void doitNotifierLeResponsable_apresCommit_quandLaDecisionReussit() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DECN1");
        String idDossier = creerDossierAdmin(ctx, "Notif", "Succes", "2015-01-01", "+22890002001");

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"REFUSEE","observation":"Incomplet","version":0}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> donneesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificateur).envoyer(eq(UUID.fromString(idDossier)), eq(TypeNotification.ADMISSION_DECISION_REFUSEE), donneesCaptor.capture());
        assertThat(donneesCaptor.getValue()).doesNotContainKey("observation");
        assertThat(donneesCaptor.getValue()).doesNotContainValue("Incomplet");
    }

    @Test
    void neDoitJamaisNotifier_quandLaDecisionEchoueEnConflitDeVersion() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DECN2");
        String idDossier = creerDossierAdmin(ctx, "Notif", "Conflit", "2015-01-02", "+22890002002");

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"REFUSEE","observation":"Incomplet","version":99}
                                """))
                .andExpect(status().isConflict());

        // La soumission du dossier a déjà déclenché son propre accusé de réception (US-06) :
        // seule une notification de *décision* doit rester absente ici.
        verify(notificateur, never())
                .envoyer(eq(UUID.fromString(idDossier)), eq(TypeNotification.ADMISSION_DECISION_REFUSEE), any());
    }

    @Test
    void neDoitJamaisNotifier_quandLaDecisionEchoueEnTransitionInvalide() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DECN3");
        String idDossier = creerDossierAdmin(ctx, "Notif", "TransitionInvalide", "2015-01-03", "+22890002003");

        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"REFUSEE","observation":"Incomplet","version":0}
                                """))
                .andExpect(status().isOk());

        verify(notificateur).envoyer(eq(UUID.fromString(idDossier)), eq(TypeNotification.ADMISSION_DECISION_REFUSEE), any());

        // Le dossier est désormais REFUSEE : REFUSEE -> REFUSEE n'est pas une
        // transition autorisée (jamais listée comme cible d'elle-même), la
        // décision est donc rejetée (422) et ne doit déclencher aucune notification.
        mockMvc.perform(post("/api/v1/demandes-admission/" + idDossier + "/decisions")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"REFUSEE","observation":"Second refus","version":1}
                                """))
                .andExpect(status().isUnprocessableEntity());

        // Toujours une seule notification (celle du premier refus), jamais une deuxième pour l'appel refusé.
        verify(notificateur, org.mockito.Mockito.times(1))
                .envoyer(eq(UUID.fromString(idDossier)), eq(TypeNotification.ADMISSION_DECISION_REFUSEE), any());
    }

    // ------------------------------------------------------------------
    // Aides (établissement dédié par test, jamais nettoyé par suppression)
    // ------------------------------------------------------------------

    private record Contexte(String etablissementId, String code, String jetonAdmin, String niveauId) {
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
        tg.novadigital.edukeys.identite.domain.Utilisateur compte = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "u.us07notif." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Utilisateur US-07 Notification Test", false));
        // Pas de flush explicite ici (mineur, correction post-revue) : cette classe n'est pas
        // @Transactional, entityManager.flush() lève donc TransactionRequiredException.
        // utilisateurRepository.save() committe déjà dans sa propre transaction (Spring Data),
        // et l'identifiant UUID v7 est généré en mémoire avant l'insertion : compte.getId()
        // est donc déjà disponible sans flush.

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

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
        entityManager.clear();
        return new Contexte(etablissementId, code, jetonAdmin, niveauId);
    }
}
