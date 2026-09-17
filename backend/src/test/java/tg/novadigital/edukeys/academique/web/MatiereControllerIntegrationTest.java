package tg.novadigital.edukeys.academique.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration des matières (US-03) : MockMvc + Testcontainers
 * PostgreSQL, rollback transactionnel (CLAUDE.md, règle 4). Couvre le
 * scénario principal (création + affectations niveau/filière avec
 * coefficients distincts), les permissions ({@code MATIERE_CONSULTER}/
 * {@code MATIERE_GERER}, y compris le refus explicite de SUPER_ADMIN et
 * PARENT), l'isolation multi-établissement, l'index unique partiel après
 * désactivation, et l'absence de N+1 sur {@code GET /matieres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MatiereControllerIntegrationTest {

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
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void activerStatistiquesHibernate(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    // ------------------------------------------------------------------
    // Scénario principal : Mathématiques coef 4 en Terminale D, coef 2 en Terminale A
    // ------------------------------------------------------------------

    @Test
    void creeUneMatiereEtDefinitSesAffectations_avecDesCoefficientsDistinctsParNiveauEtFiliere() throws Exception {
        String etablissementId = creerEtablissement("MAT");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String cycleId = creerCycle(jetonAdmin, "Lycée", "MATL", 2);
        String niveauTerminaleId = creerNiveau(jetonAdmin, "Terminale", "MATT", 3, cycleId);
        String filiereDId = creerFiliere(jetonAdmin, "Scientifique", "MATD", cycleId);
        String filiereAId = creerFiliere(jetonAdmin, "Littéraire", "MATA", cycleId);

        String reponseMatiere = mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Mathématiques","code":"MATH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("Mathématiques"))
                .andExpect(jsonPath("$.affectations.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        String matiereId = JsonPath.read(reponseMatiere, "$.id");

        mockMvc.perform(put("/api/v1/matieres/" + matiereId + "/affectations")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affectations":[
                                  {"niveauId":"%s","filiereId":"%s","coefficient":4,"obligatoire":true},
                                  {"niveauId":"%s","filiereId":"%s","coefficient":2,"obligatoire":true}
                                ]}
                                """.formatted(niveauTerminaleId, filiereDId, niveauTerminaleId, filiereAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectations.length()").value(2));

        String reponseDetail = mockMvc.perform(get("/api/v1/matieres/" + matiereId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<Number> coefficientD = JsonPath.read(reponseDetail,
                "$.affectations[?(@.filiere.id=='" + filiereDId + "')].coefficient");
        java.util.List<Number> coefficientA = JsonPath.read(reponseDetail,
                "$.affectations[?(@.filiere.id=='" + filiereAId + "')].coefficient");

        assertThat(coefficientD).extracting(Number::doubleValue).containsExactly(4.0);
        assertThat(coefficientA).extracting(Number::doubleValue).containsExactly(2.0);
    }

    // ------------------------------------------------------------------
    // Filtres GET /matieres : tronc commun (affectation « niveau seul »)
    // ------------------------------------------------------------------

    @Test
    void filtreParFiliereSeule_incluLeTroncCommunDuNiveau_maisPasUneAutreFiliere() throws Exception {
        String etablissementId = creerEtablissement("FIL1");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String cycleId = creerCycle(jetonAdmin, "Lycée", "FIL1L", 2);
        String niveauTerminaleId = creerNiveau(jetonAdmin, "Terminale", "FIL1T", 3, cycleId);
        String filiereAId = creerFiliere(jetonAdmin, "Littéraire", "FIL1A", cycleId);
        String filiereDId = creerFiliere(jetonAdmin, "Scientifique", "FIL1D", cycleId);

        String francaisId = creerMatiere(jetonAdmin, "Français");
        definirAffectationSimple(jetonAdmin, francaisId, niveauTerminaleId); // niveau seul : tronc commun

        String philosophieId = creerMatiere(jetonAdmin, "Philosophie");
        definirAffectationNiveauEtFiliere(jetonAdmin, philosophieId, niveauTerminaleId, filiereAId);

        String svtId = creerMatiere(jetonAdmin, "SVT");
        definirAffectationNiveauEtFiliere(jetonAdmin, svtId, niveauTerminaleId, filiereDId);

        String reponse = mockMvc.perform(get("/api/v1/matieres")
                        .param("filiereId", filiereAId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> libelles = JsonPath.read(reponse, "$[*].libelle");
        assertThat(libelles).contains("Français", "Philosophie");
        assertThat(libelles).doesNotContain("SVT");
    }

    @Test
    void filtreParFiliere_excluLeTroncCommunDunAutreCycle() throws Exception {
        String etablissementId = creerEtablissement("FIL2");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String cycleCollegeId = creerCycle(jetonAdmin, "Collège", "FIL2C", 1);
        String cycleLyceeId = creerCycle(jetonAdmin, "Lycée", "FIL2L", 2);
        String niveauSixiemeId = creerNiveau(jetonAdmin, "6ème", "FIL2N", 1, cycleCollegeId);
        String filiereLyceeId = creerFiliere(jetonAdmin, "Scientifique", "FIL2A", cycleLyceeId);

        String histoireId = creerMatiere(jetonAdmin, "Histoire-Géographie");
        definirAffectationSimple(jetonAdmin, histoireId, niveauSixiemeId); // 6ème, collège, niveau seul

        String reponse = mockMvc.perform(get("/api/v1/matieres")
                        .param("filiereId", filiereLyceeId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> libelles = JsonPath.read(reponse, "$[*].libelle");
        assertThat(libelles).doesNotContain("Histoire-Géographie");
    }

    @Test
    void filtreParFiliereSansCycle_nExcluAucunNiveau() throws Exception {
        String etablissementId = creerEtablissement("FIL3");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String cycleId = creerCycle(jetonAdmin, "Collège", "FIL3C", 1);
        String niveauSixiemeId = creerNiveau(jetonAdmin, "6ème", "FIL3N", 1, cycleId);
        String filiereSansCycleId = creerFiliereSansCycle(jetonAdmin, "Générale", "FIL3G");

        String anglaisId = creerMatiere(jetonAdmin, "Anglais");
        definirAffectationSimple(jetonAdmin, anglaisId, niveauSixiemeId); // niveau seul, tout cycle

        String reponse = mockMvc.perform(get("/api/v1/matieres")
                        .param("filiereId", filiereSansCycleId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> libelles = JsonPath.read(reponse, "$[*].libelle");
        assertThat(libelles).contains("Anglais");
    }

    @Test
    void filtreNiveauEtFiliereCombines_renvoieTroncCommunEtFiliereCiblee_maisPasUneAutreFiliereNiUnAutreNiveau() throws Exception {
        String etablissementId = creerEtablissement("FIL4");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String cycleId = creerCycle(jetonAdmin, "Lycée", "FIL4L", 2);
        String niveauPremiereId = creerNiveau(jetonAdmin, "Première", "FIL4P", 2, cycleId);
        String niveauTerminaleId = creerNiveau(jetonAdmin, "Terminale", "FIL4T", 3, cycleId);
        String filiereAId = creerFiliere(jetonAdmin, "Littéraire", "FIL4A", cycleId);
        String filiereDId = creerFiliere(jetonAdmin, "Scientifique", "FIL4D", cycleId);

        String francaisId = creerMatiere(jetonAdmin, "Français");
        definirAffectationSimple(jetonAdmin, francaisId, niveauTerminaleId); // tronc commun Terminale

        String philosophieId = creerMatiere(jetonAdmin, "Philosophie");
        definirAffectationNiveauEtFiliere(jetonAdmin, philosophieId, niveauTerminaleId, filiereAId);

        String svtId = creerMatiere(jetonAdmin, "SVT");
        definirAffectationNiveauEtFiliere(jetonAdmin, svtId, niveauTerminaleId, filiereDId);

        String reponseTerminaleA = mockMvc.perform(get("/api/v1/matieres")
                        .param("niveauId", niveauTerminaleId)
                        .param("filiereId", filiereAId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> libellesTerminaleA = JsonPath.read(reponseTerminaleA, "$[*].libelle");
        assertThat(libellesTerminaleA).contains("Français", "Philosophie");
        assertThat(libellesTerminaleA).doesNotContain("SVT");

        String reponsePremiereA = mockMvc.perform(get("/api/v1/matieres")
                        .param("niveauId", niveauPremiereId)
                        .param("filiereId", filiereAId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> libellesPremiereA = JsonPath.read(reponsePremiereA, "$[*].libelle");
        assertThat(libellesPremiereA).doesNotContain("Français", "Philosophie", "SVT");
    }

    @Test
    void filtreParNiveauSeul_renvoieToutesLesFilieresConfondues() throws Exception {
        String etablissementId = creerEtablissement("FIL5");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String cycleId = creerCycle(jetonAdmin, "Lycée", "FIL5L", 2);
        String niveauTerminaleId = creerNiveau(jetonAdmin, "Terminale", "FIL5T", 3, cycleId);
        String filiereAId = creerFiliere(jetonAdmin, "Littéraire", "FIL5A", cycleId);
        String filiereDId = creerFiliere(jetonAdmin, "Scientifique", "FIL5D", cycleId);

        String francaisId = creerMatiere(jetonAdmin, "Français");
        definirAffectationSimple(jetonAdmin, francaisId, niveauTerminaleId);

        String philosophieId = creerMatiere(jetonAdmin, "Philosophie");
        definirAffectationNiveauEtFiliere(jetonAdmin, philosophieId, niveauTerminaleId, filiereAId);

        String svtId = creerMatiere(jetonAdmin, "SVT");
        definirAffectationNiveauEtFiliere(jetonAdmin, svtId, niveauTerminaleId, filiereDId);

        String reponse = mockMvc.perform(get("/api/v1/matieres")
                        .param("niveauId", niveauTerminaleId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> libelles = JsonPath.read(reponse, "$[*].libelle");
        assertThat(libelles).contains("Français", "Philosophie", "SVT");
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    @Test
    void adminPeutGererLesMatieres() throws Exception {
        String etablissementId = creerEtablissement("PRA");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Physique-Chimie"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void enseignantPeutConsulterMaisPas403EnEcriture() throws Exception {
        String etablissementId = creerEtablissement("PRE");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String jetonEnseignant = creerUtilisateurAvecRoleEtObtenirToken(etablissementId, "ENSEIGNANT");

        mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Histoire-Géographie"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonEnseignant))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonEnseignant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"SVT"}
                                """))
                .andExpect(status().isForbidden());
    }

    /** Règle 11 CLAUDE.md : SUPER_ADMIN ne porte aucune permission métier (ADR-0002). */
    @Test
    void superAdminEstRefuse_enLectureCommeEnEcriture() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Anglais"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void parentEstRefuse_enLecture() throws Exception {
        String etablissementId = creerEtablissement("PRP");
        String jetonParent = creerUtilisateurAvecRoleEtObtenirToken(etablissementId, "PARENT");

        mockMvc.perform(get("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonParent))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Isolation multi-établissement
    // ------------------------------------------------------------------

    @Test
    void refuse404_quandLeNiveauAffecteAppartientAUnAutreEtablissement() throws Exception {
        String etablissementA = creerEtablissement("ISOA3");
        String etablissementB = creerEtablissement("ISOB3");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String jetonAdminB = creerAdminEtObtenirToken(etablissementB);

        String cycleIdB = creerCycle(jetonAdminB, "Collège", "ISOC3", 1);
        String niveauIdB = creerNiveau(jetonAdminB, "6ème", "ISON3", 1, cycleIdB);

        String reponseMatiere = mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Mathématiques"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String matiereId = JsonPath.read(reponseMatiere, "$.id");

        mockMvc.perform(put("/api/v1/matieres/" + matiereId + "/affectations")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affectations":[{"niveauId":"%s"}]}
                                """.formatted(niveauIdB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NIVEAU_INTROUVABLE"));
    }

    @Test
    void matiereDunAutreEtablissementEstInvisible() throws Exception {
        String etablissementA = creerEtablissement("ISOA4");
        String etablissementB = creerEtablissement("ISOB4");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String jetonAdminB = creerAdminEtObtenirToken(etablissementB);

        String reponseMatiere = mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Mathématiques"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String matiereId = JsonPath.read(reponseMatiere, "$.id");

        mockMvc.perform(get("/api/v1/matieres/" + matiereId)
                        .header("Authorization", "Bearer " + jetonAdminB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATIERE_INTROUVABLE"));
    }

    // ------------------------------------------------------------------
    // Index unique partiel : recréation possible après désactivation
    // ------------------------------------------------------------------

    @Test
    void peutRecreerUneMatiereAvecLeMemeLibelle_apresDesactivationDeLaPrecedente() throws Exception {
        String etablissementId = creerEtablissement("REC");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String reponseMatiere = mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Mathématiques"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String matiereId = JsonPath.read(reponseMatiere, "$.id");

        mockMvc.perform(post("/api/v1/matieres/" + matiereId + "/desactivation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Mathématiques"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("Mathématiques"));
    }

    // ------------------------------------------------------------------
    // Anti N+1
    // ------------------------------------------------------------------

    @Test
    void listerMatieres_emetLeMemeNombreDeRequetesSql_quelQueSoitLeNombreDeMatieres() throws Exception {
        String etablissementId = creerEtablissement("NPM");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String cycleId = creerCycle(jetonAdmin, "Collège", "NPMC", 1);

        for (int i = 0; i < 3; i++) {
            String niveauId = creerNiveau(jetonAdmin, "Niveau " + i, "NPMN" + i, i + 1, cycleId);
            String matiereId = creerMatiere(jetonAdmin, "Matière " + i);
            definirAffectationSimple(jetonAdmin, matiereId, niveauId);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        long requetesAvecTroisMatieres = stats.getPrepareStatementCount();

        for (int i = 3; i < 10; i++) {
            String niveauId = creerNiveau(jetonAdmin, "Niveau " + i, "NPMN" + i, i + 1, cycleId);
            String matiereId = creerMatiere(jetonAdmin, "Matière " + i);
            definirAffectationSimple(jetonAdmin, matiereId, niveauId);
        }

        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        long requetesAvecDixMatieres = stats.getPrepareStatementCount();

        assertThat(requetesAvecDixMatieres)
                .withFailMessage(
                        "Le nombre de requêtes SQL doit être identique quel que soit le nombre de matières "
                                + "(%d avec 3 matières, %d avec 10 matières) : un écart révèle un N+1 sur les affectations.",
                        requetesAvecTroisMatieres, requetesAvecDixMatieres)
                .isEqualTo(requetesAvecTroisMatieres);
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private String creerMatiere(String jetonAdmin, String libelle) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/matieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s"}
                                """.formatted(libelle)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private void definirAffectationSimple(String jetonAdmin, String matiereId, String niveauId) throws Exception {
        mockMvc.perform(put("/api/v1/matieres/" + matiereId + "/affectations")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affectations":[{"niveauId":"%s"}]}
                                """.formatted(niveauId)))
                .andExpect(status().isOk());
    }

    private void definirAffectationNiveauEtFiliere(String jetonAdmin, String matiereId, String niveauId, String filiereId)
            throws Exception {
        mockMvc.perform(put("/api/v1/matieres/" + matiereId + "/affectations")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"affectations":[{"niveauId":"%s","filiereId":"%s"}]}
                                """.formatted(niveauId, filiereId)))
                .andExpect(status().isOk());
    }

    private String creerFiliereSansCycle(String jetonAdmin, String libelle, String code) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/filieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s"}
                                """.formatted(libelle, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
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

    private String creerFiliere(String jetonAdmin, String libelle, String code, String cycleId) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/filieres")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s","cycleId":"%s"}
                                """.formatted(libelle, code, cycleId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
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
        return creerUtilisateurAvecRoleEtObtenirToken(etablissementId, "ADMIN");
    }

    private String creerUtilisateurAvecRoleEtObtenirToken(String etablissementId, String roleCode) throws Exception {
        tg.novadigital.edukeys.identite.domain.Utilisateur compte = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "utilisateur.us03." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Utilisateur US-03 Test", false));
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
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(email, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(reponseLogin, "$.accessToken");
    }
}
