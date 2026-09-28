package tg.novadigital.edukeys.admission.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
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

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.admission.domain.DecisionAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.common.audit.HistoriqueService;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Deux cas US-07 volontairement séparés de {@link DecisionAdmissionIntegrationTest}
 * (correction post-revue), tous deux incompatibles avec son rollback
 * transactionnel : établissement dédié par test, jamais nettoyé par
 * suppression (CLAUDE.md, règle 4), même patron que {@link
 * DecisionAdmissionNotificationIntegrationTest}.
 *
 * <ul>
 *   <li>Historisation Envers : les tables {@code _aud} ne sont écrites qu'au
 *   commit — dans un test à rollback, la révision n'existe jamais.</li>
 *   <li>Collision {@code uk_demandes_admission_doublon} : la violation de
 *   contrainte, une fois remontée par {@code decider()} en {@code
 *   ConflitException}, empoisonne la transaction PostgreSQL en cours
 *   (25P02) — toute requête suivante dans la <em>même</em> transaction (ex.
 *   {@code jdbcTemplate.queryForObject} de vérification) échoue à son tour.
 *   Le service lui-même est correct (il ne relit jamais après l'échec du
 *   flush) ; c'est la transaction <em>partagée avec le test</em> qui posait
 *   problème, pas le service.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationTurnstileDoubleTest.class)
class DecisionAdmissionEnversEtDoublonIntegrationTest {

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

    @Autowired
    private HistoriqueService historiqueService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void doitHistoriserLesRevisions_dansLesTablesAuditDemandeEtDecision() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC13");
        String idDossier = creerDossierAdmin(ctx, "Nutifafa", "Komla", "2015-01-15", "+22890003013");

        decider(ctx, idDossier, "REFUSEE", "Incomplet pour revision", 0).andExpect(status().isOk());

        try (tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement portee =
                     ContexteEtablissement.ouvrir(UUID.fromString(ctx.etablissementId()))) {
            List<RevisionHistorique<DemandeAdmission>> revisionsDemande =
                    historiqueService.historique(DemandeAdmission.class, UUID.fromString(idDossier));
            assertThat(revisionsDemande).hasSizeGreaterThanOrEqualTo(2);
            assertThat(revisionsDemande.get(revisionsDemande.size() - 1).entite().getStatut().name()).isEqualTo("REFUSEE");
        }

        UUID idDecision = UUID.fromString(jdbcTemplate.queryForObject(
                "select id from decisions_admission where demande_id = ?::uuid", String.class, idDossier));
        try (tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement portee =
                     ContexteEtablissement.ouvrir(UUID.fromString(ctx.etablissementId()))) {
            List<RevisionHistorique<DecisionAdmission>> revisionsDecision =
                    historiqueService.historique(DecisionAdmission.class, idDecision);
            assertThat(revisionsDecision).hasSize(1);
            assertThat(revisionsDecision.get(0).entite().getStatutNouveau().name()).isEqualTo("REFUSEE");
        }
    }

    @Test
    void doitRejeter409_quandRepasserEnListeAttenteEntreEnCollisionAvecUnAutreDossierActif() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DEC14");
        String idPremierDossier = creerDossierAdmin(ctx, "Doublon", "Enfant", "2015-05-20", "+22890003014");

        // Le premier dossier sort de l'index partiel (REFUSEE n'y figure plus).
        decider(ctx, idPremierDossier, "REFUSEE", "Premier refus", 0).andExpect(status().isOk());

        // Un second dossier, même enfant, redevient possible (EN_ATTENTE).
        String idSecondDossier = creerDossierAdmin(ctx, "Doublon", "Enfant", "2015-05-20", "+22890003015");
        assertThat(idSecondDossier).isNotEqualTo(idPremierDossier);

        // Repasser le premier dossier en LISTE_ATTENTE le fait rentrer dans
        // l'index partiel, en collision avec le second (EN_ATTENTE). Chaque
        // requête MockMvc s'exécute dans sa propre transaction (classe non
        // transactionnelle) : la violation de contrainte de celle-ci ne
        // contamine donc pas la vérification qui suit.
        decider(ctx, idPremierDossier, "LISTE_ATTENTE", "Reconsideration", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMISSION_DOUBLON"));

        Long nombreDeDecisionsDuPremierDossier = jdbcTemplate.queryForObject(
                "select count(*) from decisions_admission where demande_id = ?::uuid", Long.class, idPremierDossier);
        assertThat(nombreDeDecisionsDuPremierDossier).isEqualTo(1L); // seul le premier refus, pas la tentative en collision
        String statutPremierDossier = jdbcTemplate.queryForObject(
                "select statut from demandes_admission where id = ?::uuid", String.class, idPremierDossier);
        assertThat(statutPremierDossier).isEqualTo("REFUSEE"); // inchangé par la tentative refusée
    }

    // ------------------------------------------------------------------
    // Aides (établissement dédié par test, jamais nettoyé par suppression)
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
        tg.novadigital.edukeys.identite.domain.Utilisateur compte = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "u.us07envd." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Utilisateur US-07 Envers/Doublon Test", false));
        // Pas de flush explicite (classe non transactionnelle) : utilisateurRepository.save()
        // committe déjà dans sa propre transaction, et l'identifiant UUID v7 est généré en
        // mémoire avant l'insertion (compte.getId() est déjà disponible).

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
                                {"email":"%s","motDePasse":"%s"}
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
