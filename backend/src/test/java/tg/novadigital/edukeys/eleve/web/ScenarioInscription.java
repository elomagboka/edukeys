package tg.novadigital.edukeys.eleve.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Mise en scène partagée des tests d'intégration de l'inscription (US-08) : établissement, administrateur,
 * structure académique, dossiers acceptés. Tout passe par l'API réelle ou par des insertions JDBC, jamais par
 * un contournement des services. Utilisable dans une transaction de test (rollback) comme hors transaction
 * (vraies transactions concurrentes) ; dans ce second cas chaque scénario crée son propre établissement.
 */
final class ScenarioInscription {

    static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    static final String MOT_DE_PASSE = "Password123!";

    private static final AtomicInteger TELEPHONE = new AtomicInteger(1000);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    ScenarioInscription(MockMvc mockMvc, JdbcTemplate jdbcTemplate, UtilisateurRepository utilisateurRepository,
                        PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Établissement prêt à inscrire : admin, année active 2026-2027, cycle, niveau et admissions ouvertes. */
    record Etablissement(String id, String code, String jetonAdmin, String cycleId, String niveauId) {
    }

    record Dossier(String id, long version) {
    }

    // ------------------------------------------------------------------
    // Établissement et structure
    // ------------------------------------------------------------------

    Etablissement etablissementPret(String prefixe) throws Exception {
        String id = creerEtablissement(prefixe);
        String code = jdbcTemplate.queryForObject("select code from etablissements where id = ?::uuid", String.class, id);
        String jetonAdmin = jetonPourRole(id, "ADMIN");
        creerAnnee(jetonAdmin, "2026-09-01", "2027-07-15", true);
        String cycleId = creerCycle(jetonAdmin, "Collège", prefixe + "C", 1);
        String niveauId = creerNiveau(jetonAdmin, "6ème", prefixe + "N", 1, cycleId);
        jdbcTemplate.update("update etablissements set admissions_ouvertes = true where id = ?::uuid", id);
        return new Etablissement(id, code, jetonAdmin, cycleId, niveauId);
    }

    String creerEtablissement(String prefixeCode) throws Exception {
        String jetonSuperAdmin = connecter(EMAIL_SUPER_ADMIN);
        // 3 lettres + 5 chiffres au plus : reste dans [A-Z0-9]{2,10} (US-08).
        String code = prefixeCode + Math.abs(System.nanoTime() % 100000);
        String reponse = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement %s","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg",
                                 "emailAdministrateur":"admin.%s@edukeys.tg","nomCompletAdministrateur":"Admin Test"}
                                """.formatted(code, code, code.toLowerCase(), code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.etablissement.id");
    }

    String idSitePrincipal(String etablissementId) {
        return jdbcTemplate.queryForObject(
                "select id::text from sites where etablissement_id = ?::uuid and principal = true and actif = true", String.class, etablissementId);
    }

    String creerAnnee(String jeton, String debut, String fin, boolean activer) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"%s","dateFin":"%s"}
                                """.formatted(debut, fin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(reponse, "$.id");
        if (activer) {
            mockMvc.perform(post("/api/v1/annees-scolaires/" + id + "/activation")
                            .header("Authorization", "Bearer " + jeton))
                    .andExpect(status().isOk());
        }
        return id;
    }

    String creerCycle(String jeton, String libelle, String code, int rang) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s","rang":%d}
                                """.formatted(libelle, code, rang)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    String creerNiveau(String jeton, String libelle, String code, int rang, String cycleId) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/niveaux")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s","rang":%d,"cycleId":"%s"}
                                """.formatted(libelle, code, rang, cycleId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    /** @param effectifMax {@code null} : sans limite ; @param anneeId {@code null} : année active */
    String creerClasse(String jeton, String niveauId, String suffixe, Integer effectifMax, String anneeId) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suffixe":"%s","niveauId":"%s","effectifMax":%s,"anneeScolaireId":%s}
                                """.formatted(suffixe, niveauId, effectifMax, anneeId == null ? "null" : "\"" + anneeId + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    // ------------------------------------------------------------------
    // Comptes
    // ------------------------------------------------------------------

    String jetonPourRole(String etablissementId, String roleCode) throws Exception {
        Utilisateur compte = utilisateurRepository.save(new Utilisateur(
                "u.us08." + UUID.randomUUID() + "@edukeys.tg", passwordEncoder.encode(MOT_DE_PASSE), "Utilisateur US-08 Test", false));
        // Dans une transaction de test, l'insertion du compte est différée : la FK de l'affectation (JDBC) l'exige écrite.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            entityManager.flush();
        }
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, roleCode);
        return connecter(compte.getEmail());
    }

    String connecter(String identifiant) throws Exception {
        return connecter(identifiant, MOT_DE_PASSE);
    }

    String connecter(String identifiant, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifiant":"%s","motDePasse":"%s"}
                                """.formatted(identifiant, motDePasse)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.accessToken");
    }

    // ------------------------------------------------------------------
    // Dossiers
    // ------------------------------------------------------------------

    Dossier dossierEnAttente(Etablissement etab, String nom, String prenoms, String dateNaissance) throws Exception {
        String telephone = "+2289001" + TELEPHONE.incrementAndGet();
        String json = """
                {"niveauId":"%s","nom":"%s","prenoms":"%s","dateNaissance":"%s","lieuNaissance":"Lomé","sexe":"F",
                 "nationalite":"TG","responsableNom":"Responsable","responsablePrenoms":"Test","responsableLien":"PERE",
                 "responsableTelephone":"%s","responsableEmail":"responsable@example.com","consentementDonnees":true}
                """.formatted(etab.niveauId(), nom, prenoms, dateNaissance, telephone);
        MockMultipartFile demande = new MockMultipartFile("demande", "demande.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf",
                "%PDF-1.4\n1 0 obj <<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1));
        String reponse = mockMvc.perform(multipart("/api/v1/demandes-admission")
                        .file(demande).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                        .header("Authorization", "Bearer " + etab.jetonAdmin()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Dossier(JsonPath.read(reponse, "$.id"), ((Number) JsonPath.read(reponse, "$.version")).longValue());
    }

    Dossier dossierAccepte(Etablissement etab, String nom, String prenoms, String dateNaissance) throws Exception {
        Dossier enAttente = dossierEnAttente(etab, nom, prenoms, dateNaissance);
        mockMvc.perform(post("/api/v1/demandes-admission/" + enAttente.id() + "/decisions")
                        .header("Authorization", "Bearer " + etab.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"statut":"ACCEPTEE","observation":null,"version":%d}
                                """.formatted(enAttente.version())))
                .andExpect(status().isOk());
        return relire(etab.jetonAdmin(), enAttente.id());
    }

    Dossier relire(String jeton, String dossierId) throws Exception {
        String reponse = mockMvc.perform(get("/api/v1/demandes-admission/" + dossierId)
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Dossier(dossierId, ((Number) JsonPath.read(reponse, "$.version")).longValue());
    }

    // ------------------------------------------------------------------
    // Inscription
    // ------------------------------------------------------------------

    ResultActions inscrire(String jeton, String dossierId, String classeId, long version, boolean confirmerHomonyme) throws Exception {
        return mockMvc.perform(post("/api/v1/inscriptions")
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps(dossierId, classeId, version, confirmerHomonyme)));
    }

    static String corps(String dossierId, String classeId, long version, boolean confirmerHomonyme) {
        return """
                {"demandeAdmissionId":"%s","classeId":"%s","versionDemande":%d,"confirmerHomonyme":%s}
                """.formatted(dossierId, classeId, version, confirmerHomonyme);
    }
}
