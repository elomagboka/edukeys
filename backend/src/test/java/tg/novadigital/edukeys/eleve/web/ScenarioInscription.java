package tg.novadigital.edukeys.eleve.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;
import tg.novadigital.edukeys.testsupport.EtablissementJetable;

/**
 * Mise en scène partagée des tests d'intégration de l'inscription (US-08) : établissement, administrateur,
 * structure académique, dossiers acceptés. Tout passe par l'API réelle ou par des insertions JDBC, jamais par
 * un contournement des services. Utilisable dans une transaction de test (rollback) comme hors transaction
 * (vraies transactions concurrentes) ; dans ce second cas chaque scénario crée son propre établissement.
 * Établissement jetable et comptes passent par {@link EtablissementJetable}, motif obligatoire des tests
 * hors rollback (compteurs) : sa Javadoc fait foi.
 */
final class ScenarioInscription {

    static final String EMAIL_SUPER_ADMIN = EtablissementJetable.EMAIL_SUPER_ADMIN;
    static final String MOT_DE_PASSE = EtablissementJetable.MOT_DE_PASSE;

    private static final AtomicInteger TELEPHONE = new AtomicInteger(1000);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final EtablissementJetable etablissements;

    ScenarioInscription(MockMvc mockMvc, JdbcTemplate jdbcTemplate, UtilisateurRepository utilisateurRepository,
                        PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.etablissements = new EtablissementJetable(mockMvc, jdbcTemplate, utilisateurRepository, passwordEncoder, entityManager);
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
        String code = etablissements.code(id);
        String jetonAdmin = jetonPourRole(id, "ADMIN");
        creerAnnee(jetonAdmin, "2026-09-01", "2027-07-15", true);
        String cycleId = creerCycle(jetonAdmin, "Collège", prefixe + "C", 1);
        String niveauId = creerNiveau(jetonAdmin, "6ème", prefixe + "N", 1, cycleId);
        jdbcTemplate.update("update etablissements set admissions_ouvertes = true where id = ?::uuid", id);
        return new Etablissement(id, code, jetonAdmin, cycleId, niveauId);
    }

    String creerEtablissement(String prefixeCode) throws Exception {
        return etablissements.creerEtablissement(prefixeCode);
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
    String creerFiliere(String jeton, String libelle, String code) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/filieres")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s"}
                                """.formatted(libelle, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    String creerClasseAvecFiliere(String jeton, String niveauId, String suffixe, String filiereId) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suffixe":"%s","niveauId":"%s","filiereId":"%s"}
                                """.formatted(suffixe, niveauId, filiereId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }


    // ------------------------------------------------------------------
    // Comptes
    // ------------------------------------------------------------------

    String jetonPourRole(String etablissementId, String roleCode) throws Exception {
        return etablissements.jetonPourRole(etablissementId, roleCode);
    }

    String connecter(String identifiant) throws Exception {
        return etablissements.connecter(identifiant);
    }

    String connecter(String identifiant, String motDePasse) throws Exception {
        return etablissements.connecter(identifiant, motDePasse);
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
