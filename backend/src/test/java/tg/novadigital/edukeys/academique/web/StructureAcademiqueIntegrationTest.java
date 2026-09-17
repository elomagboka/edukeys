package tg.novadigital.edukeys.academique.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Tests d'intégration de la structure académique (US-02, issue #22) :
 * MockMvc + Testcontainers PostgreSQL, rollback transactionnel (CLAUDE.md,
 * règle 4). Couvre la hiérarchie Cycle {@literal >} Niveau {@literal >} Classe
 * de bout en bout, l'isolation multi-établissement, les permissions
 * ({@code STRUCTURE_ACADEMIQUE_*}, y compris le refus explicite de
 * SUPER_ADMIN) et les deux points bloquants signalés par le tech lead :
 * l'isolation de {@code site_id} (R11) et l'absence de N+1 sur
 * {@code GET /classes} avec un comptage de requêtes SQL constant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StructureAcademiqueIntegrationTest {

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
    // Critère d'acceptation : hiérarchie Cycle > Niveau > Classe de bout en bout
    // ------------------------------------------------------------------

    @Test
    void creeUnCycleUnNiveauEtUneClasse_respectantLaHierarchieCycleNiveauClasse() throws Exception {
        String etablissementId = creerEtablissement("HIE");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        creerEtActiverAnneeScolaire(jetonAdmin);

        String reponseCycle = mockMvc.perform(post("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Collège","code":"COL","rang":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("Collège"))
                .andReturn().getResponse().getContentAsString();
        String cycleId = JsonPath.read(reponseCycle, "$.id");

        String reponseNiveau = mockMvc.perform(post("/api/v1/niveaux")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"6ème","code":"6E","rang":1,"cycleId":"%s"}
                                """.formatted(cycleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycleId").value(cycleId))
                .andExpect(jsonPath("$.cycleLibelle").value("Collège"))
                .andReturn().getResponse().getContentAsString();
        String niveauId = JsonPath.read(reponseNiveau, "$.id");

        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"6ème A","suffixe":"A","niveauId":"%s","effectifMax":40}
                                """.formatted(niveauId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("6ème A"))
                .andExpect(jsonPath("$.niveauId").value(niveauId))
                .andExpect(jsonPath("$.cycleId").value(cycleId))
                .andExpect(jsonPath("$.cycleLibelle").value("Collège"));
    }

    /** D5 : libellé de classe composé quand absent (niveau.libelle + suffixe). */
    @Test
    void creeUneClasse_composeLeLibelle_quandLibelleAbsent() throws Exception {
        String etablissementId = creerEtablissement("CMP");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        creerEtActiverAnneeScolaire(jetonAdmin);
        String cycleId = creerCycle(jetonAdmin, "Collège", "COL1", 1);
        String niveauId = creerNiveau(jetonAdmin, "6ème", "6E1", 1, cycleId);

        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suffixe":"B","niveauId":"%s"}
                                """.formatted(niveauId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("6ème B"));
    }

    // ------------------------------------------------------------------
    // R13 : refus de désactivation en cascade
    // ------------------------------------------------------------------

    @Test
    void refuseDesactivationDunCycle_quandIlPorteEncoreUnNiveauActif() throws Exception {
        String etablissementId = creerEtablissement("CSC");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String cycleId = creerCycle(jetonAdmin, "Collège", "COL2", 1);
        creerNiveau(jetonAdmin, "6ème", "6E2", 1, cycleId);

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/desactivation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CYCLE_NON_DESACTIVABLE"));
    }

    // ------------------------------------------------------------------
    // LE TEST LE PLUS IMPORTANT : isolation par site_id (R11)
    // ------------------------------------------------------------------

    /**
     * Arbitrage tech lead : « site_id est la seule valeur du corps de requête
     * que le filtre Hibernate ne protège pas ». Créer une classe avec le
     * site_id d'un AUTRE établissement doit être refusé, même si ce site
     * existe réellement en base.
     */
    @Test
    void refuseCreationDeClasse_quandSiteIdAppartientAUnAutreEtablissement() throws Exception {
        String etablissementA = creerEtablissement("SITA");
        String etablissementB = creerEtablissement("SITB");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String jetonAdminB = creerAdminEtObtenirToken(etablissementB);

        String siteDeB = idSitePrincipal(jetonAdminB, etablissementB);

        creerEtActiverAnneeScolaire(jetonAdminA);
        String cycleId = creerCycle(jetonAdminA, "Collège", "COLX", 1);
        String niveauId = creerNiveau(jetonAdminA, "6ème", "6EX", 1, cycleId);

        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"6ème A","niveauId":"%s","siteId":"%s","effectifMax":40}
                                """.formatted(niveauId, siteDeB)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLASSE_SITE_INVALIDE"));
    }

    @Test
    void accepteCreationDeClasse_quandSiteIdAppartientALetablissementCourant() throws Exception {
        String etablissementA = creerEtablissement("SITC");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String siteDeA = idSitePrincipal(jetonAdminA, etablissementA);

        creerEtActiverAnneeScolaire(jetonAdminA);
        String cycleId = creerCycle(jetonAdminA, "Collège", "COLY", 1);
        String niveauId = creerNiveau(jetonAdminA, "6ème", "6EY", 1, cycleId);

        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"6ème A","niveauId":"%s","siteId":"%s","effectifMax":40}
                                """.formatted(niveauId, siteDeA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteId").value(siteDeA));
    }

    // ------------------------------------------------------------------
    // Test anti-N+1 renforcé : comptage de requêtes SQL CONSTANT
    // ------------------------------------------------------------------

    /**
     * Exigence renforcée du tech lead : pas de seuil fixe (resterait vert
     * avec un N+1 tant que le jeu d'essai est petit), mais un comptage
     * identique entre deux volumes de données différents. {@code clear()}
     * avant chaque mesure : sans lui, le cache de premier niveau masquerait
     * le N+1 (piège documenté dans docs/JOURNAL.md du 2026-09-12).
     */
    @Test
    void listerClasses_emetLeMemeNombreDeRequetesSql_quelQueSoitLeNombreDeClasses() throws Exception {
        String etablissementId = creerEtablissement("NPL");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        creerEtActiverAnneeScolaire(jetonAdmin);
        String cycleId = creerCycle(jetonAdmin, "Collège", "NPLC", 1);
        String cycleId2 = creerCycle(jetonAdmin, "Lycée", "NPLL", 2);
        String filiereId = creerFiliere(jetonAdmin, "Scientifique", "NPLD", cycleId2);

        // Premier jeu : 3 classes, CHACUNE sur un niveau distinct (un niveau
        // partagé entre plusieurs classes serait mis en cache par Hibernate
        // après le premier accès LAZY, masquant un N+1 même sans
        // @EntityGraph : la diversité des niveaux est ce qui rend ce test
        // capable de détecter la régression, pas seulement le nombre de
        // classes).
        for (int i = 0; i < 3; i++) {
            String niveau = creerNiveau(jetonAdmin, "Niveau " + i, "NPLN" + i, i + 1, cycleId);
            creerClasse(jetonAdmin, "Classe " + i, niveau, null);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        long requetesAvecTroisClasses = stats.getPrepareStatementCount();

        // Second jeu : 7 classes de plus (10 au total), chacune sur un
        // nouveau niveau distinct, certaines avec une filière.
        for (int i = 3; i < 10; i++) {
            String niveau = creerNiveau(jetonAdmin, "Niveau " + i, "NPLN" + i, i + 1, cycleId2);
            creerClasse(jetonAdmin, "Classe " + i, niveau, i % 2 == 0 ? filiereId : null);
        }

        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        long requetesAvecDixClasses = stats.getPrepareStatementCount();

        assertThat(requetesAvecDixClasses)
                .withFailMessage(
                        "Le nombre de requêtes SQL doit être identique quel que soit le nombre de classes "
                                + "(%d avec 3 classes, %d avec 10 classes) : un écart révèle un N+1 sur les relations "
                                + "LAZY niveau/cycle/filiere/anneeScolaire.",
                        requetesAvecTroisClasses, requetesAvecDixClasses)
                .isEqualTo(requetesAvecTroisClasses);
    }

    @Test
    void listerNiveaux_emetLeMemeNombreDeRequetesSql_quelQueSoitLeNombreDeNiveaux() throws Exception {
        String etablissementId = creerEtablissement("NPN");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        // Un cycle DISTINCT par niveau (et non un cycle partagé) : sans
        // @EntityGraph sur NiveauRepository, un cycle unique serait chargé
        // une seule fois puis servi par le cache de premier niveau, et le
        // comptage resterait constant même en présence d'un N+1 réel.
        for (int i = 0; i < 3; i++) {
            String cycleId = creerCycle(jetonAdmin, "Cycle " + i, "NPNC" + i, i + 1);
            creerNiveau(jetonAdmin, "Niveau " + i, "NPN" + i, i + 1, cycleId);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/niveaux")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        long requetesAvecTroisNiveaux = stats.getPrepareStatementCount();

        for (int i = 3; i < 10; i++) {
            String cycleId = creerCycle(jetonAdmin, "Cycle " + i, "NPNC" + i, i + 1);
            creerNiveau(jetonAdmin, "Niveau " + i, "NPN" + i, i + 1, cycleId);
        }

        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/niveaux")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        long requetesAvecDixNiveaux = stats.getPrepareStatementCount();

        assertThat(requetesAvecDixNiveaux).isEqualTo(requetesAvecTroisNiveaux);
    }

    @Test
    void listerFilieres_emetLeMemeNombreDeRequetesSql_quelQueSoitLeNombreDeFilieres() throws Exception {
        String etablissementId = creerEtablissement("NPF");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        // Un cycle DISTINCT par filière, même raison que pour les niveaux :
        // un cycle partagé serait mis en cache dès le premier accès LAZY et
        // masquerait un N+1 réel.
        for (int i = 0; i < 3; i++) {
            String cycleId = creerCycle(jetonAdmin, "Cycle " + i, "NPFC" + i, i + 1);
            creerFiliere(jetonAdmin, "Filière " + i, "NPF" + i, cycleId);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/filieres")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        long requetesAvecTroisFilieres = stats.getPrepareStatementCount();

        for (int i = 3; i < 10; i++) {
            String cycleId = creerCycle(jetonAdmin, "Cycle " + i, "NPFC" + i, i + 1);
            creerFiliere(jetonAdmin, "Filière " + i, "NPF" + i, cycleId);
        }

        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/filieres")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
        long requetesAvecDixFilieres = stats.getPrepareStatementCount();

        assertThat(requetesAvecDixFilieres)
                .withFailMessage(
                        "Le nombre de requêtes SQL doit être identique quel que soit le nombre de filières "
                                + "(%d avec 3 filières, %d avec 10 filières) : un écart révèle un N+1 sur la relation "
                                + "LAZY cycle.",
                        requetesAvecTroisFilieres, requetesAvecDixFilieres)
                .isEqualTo(requetesAvecTroisFilieres);
    }

    // ------------------------------------------------------------------
    // Isolation par site_id (R11) sur la MODIFICATION d'une classe
    // ------------------------------------------------------------------

    /**
     * Même arbitrage que pour la création (R11), sur le second chemin
     * d'écriture : {@code PUT /api/v1/classes/{id}}. C'est exactement le
     * scénario qui a laissé passer la faille d'US-04 (un second chemin
     * d'écriture non testé).
     */
    @Test
    void refuseModificationDeClasse_quandSiteIdAppartientAUnAutreEtablissement() throws Exception {
        String etablissementA = creerEtablissement("MSITA");
        String etablissementB = creerEtablissement("MSITB");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String jetonAdminB = creerAdminEtObtenirToken(etablissementB);

        String siteDeA = idSitePrincipal(jetonAdminA, etablissementA);
        String siteDeB = idSitePrincipal(jetonAdminB, etablissementB);

        creerEtActiverAnneeScolaire(jetonAdminA);
        String cycleId = creerCycle(jetonAdminA, "Collège", "MSITC", 1);
        String niveauId = creerNiveau(jetonAdminA, "6ème", "MSITN", 1, cycleId);

        String reponseClasse = mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"6ème A","niveauId":"%s","siteId":"%s","effectifMax":40}
                                """.formatted(niveauId, siteDeA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String classeId = JsonPath.read(reponseClasse, "$.id");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/classes/" + classeId)
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"6ème A","niveauId":"%s","siteId":"%s","effectifMax":40}
                                """.formatted(niveauId, siteDeB)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLASSE_SITE_INVALIDE"));

        String siteEnBase = jdbcTemplate.queryForObject(
                "select site_id from classes where id = ?::uuid", String.class, classeId);
        assertThat(siteEnBase).isEqualTo(siteDeA);
    }

    // ------------------------------------------------------------------
    // Isolation multi-établissement classique
    // ------------------------------------------------------------------

    @Test
    void refuse404_surUnCycleDunAutreEtablissement_nonModifiableNiVisible() throws Exception {
        String etablissementA = creerEtablissement("ISOA2");
        String etablissementB = creerEtablissement("ISOB2");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String jetonAdminB = creerAdminEtObtenirToken(etablissementB);

        String cycleIdA = creerCycle(jetonAdminA, "Collège", "ISOC", 1);

        mockMvc.perform(get("/api/v1/cycles/" + cycleIdA)
                        .header("Authorization", "Bearer " + jetonAdminB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CYCLE_INTROUVABLE"));
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    @Test
    void refuse403_unAppelantSansPermission() throws Exception {
        String etablissementId = creerEtablissement("PRM2");
        String jetonSansPermission = creerUtilisateurSansRoleEtObtenirToken(etablissementId);

        mockMvc.perform(get("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jetonSansPermission))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jetonSansPermission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Collège","rang":1}
                                """))
                .andExpect(status().isForbidden());
    }

    /** Règle 11 CLAUDE.md : SUPER_ADMIN ne porte ni STRUCTURE_ACADEMIQUE_CONSULTER ni _GERER (ADR-0002). */
    @Test
    void refuse403_unSuperAdmin_surLesEndpointsDeStructureAcademique() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"Collège","rang":1}
                                """))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private String creerClasse(String jetonAdmin, String libelle, String niveauId, String filiereId) throws Exception {
        String filiereJson = filiereId == null ? "" : ",\"filiereId\":\"" + filiereId + "\"";
        mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","niveauId":"%s","effectifMax":40%s}
                                """.formatted(libelle, niveauId, filiereJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return null;
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

    private String idSitePrincipal(String jetonAdmin, String etablissementId) throws Exception {
        String reponse = mockMvc.perform(get("/api/v1/etablissements/" + etablissementId + "/sites")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$[0].id");
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
                        "admin.us02." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Admin US-02 Test", false));
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
                        "sanspermission.us02." + UUID.randomUUID() + "@edukeys.tg",
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
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(email, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(reponseLogin, "$.accessToken");
    }
}
