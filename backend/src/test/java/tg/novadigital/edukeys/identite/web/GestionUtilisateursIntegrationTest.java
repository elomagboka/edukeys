package tg.novadigital.edukeys.identite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

import tg.novadigital.edukeys.common.securite.limitation.FiltreLimitationDebit;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration de la gestion des comptes utilisateurs et des rôles
 * RBAC (US-04, issue #24), au-delà du socle déjà couvert par
 * {@code AuthControllerIntegrationTest} et {@code IsolationUtilisateursTest}.
 *
 * <p>Deux établissements de démonstration (CSJ / ESN, {@code R__etablissement_demo.sql})
 * et deux comptes (directeur DIRECTION sur CSJ, enseignant.parent ENSEIGNANT+PARENT
 * sur CSJ), tous mot de passe {@code Password123!}. Aucun compte ADMIN de
 * démonstration : chaque test qui en a besoin en crée un directement en base
 * (JDBC), suivant exactement le patron d'{@code AuthControllerIntegrationTest}.</p>
 *
 * <p>Nettoyage par rollback transactionnel (CLAUDE.md, règle 4).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GestionUtilisateursIntegrationTest {

    private static final UUID ETABLISSEMENT_A = UUID.fromString("01977000-0000-7000-9000-000000000001");
    private static final UUID ETABLISSEMENT_B = UUID.fromString("01977000-0000-7000-9000-000000000002");
    private static final String MOT_DE_PASSE = "Password123!";
    private static final String EMAIL_DIRECTEUR = "directeur@edukeys.tg";
    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String EMAIL_ENSEIGNANT_PARENT = "enseignant.parent@edukeys.tg";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private FiltreLimitationDebit filtreLimitationDebit;

    @DynamicPropertySource
    static void activerStatistiquesHibernate(DynamicPropertyRegistry registry) {
        // Nécessaire pour le test N+1 ci-dessous : compter les requêtes SQL
        // émises par Hibernate pendant un appel, sans introduire de nouveau
        // mécanisme (aucun intercepteur de datasource n'existe déjà dans le
        // projet, voir la recherche préalable).
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @BeforeEach
    void reinitialiserLaLimitationDeDebit() {
        filtreLimitationDebit.reinitialiserPourLesTests();
    }

    private String connecter(String email, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(email, motDePasse)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.accessToken");
    }

    private String connecter(String email) throws Exception {
        return connecter(email, MOT_DE_PASSE);
    }

    /** Compte + affectation active sur un établissement, avec les rôles donnés, inséré directement en base. */
    private UUID creerCompteAffecte(UUID etablissementId, String email, String... roles) {
        Utilisateur utilisateur = new Utilisateur(email, passwordEncoder.encode(MOT_DE_PASSE), "Compte Test " + email, false);
        utilisateur = utilisateurRepository.save(utilisateur);
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, utilisateur.getId(), etablissementId);
        for (String role : roles) {
            jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, role);
        }
        return utilisateur.getId();
    }

    private UUID creerAdminSurEtablissementA(String email) {
        return creerCompteAffecte(ETABLISSEMENT_A, email, "ADMIN");
    }

    // ==================================================================
    // Cloisonnement multi-établissement (via HTTP)
    // ==================================================================

    @Test
    void listeMonEtablissement_neContientAucunCompteDeLautreEtablissement() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.liste.a." + UUID.randomUUID() + "@edukeys.tg");
        String compteB = "compte.liste.b." + UUID.randomUUID() + "@edukeys.tg";
        creerCompteAffecte(ETABLISSEMENT_B, compteB, "DIRECTION");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .param("size", "200")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[?(@.email == '" + compteB + "')]").doesNotExist());
    }

    @Test
    void obtenirMonEtablissement_repond404AvecLeMemeMessage_pourUnCompteDeLautreEtablissementOuInexistant() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.404.a." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteBId = creerCompteAffecte(ETABLISSEMENT_B, "compte.404.b." + UUID.randomUUID() + "@edukeys.tg", "DIRECTION");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        String messageCompteAutreEtablissement = mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + compteBId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String messageCompteInexistant = mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String detailAutreEtablissement = JsonPath.read(messageCompteAutreEtablissement, "$.detail");
        String detailInexistant = JsonPath.read(messageCompteInexistant, "$.detail");
        assertThat(detailAutreEtablissement).isEqualTo(detailInexistant);
    }

    @Test
    void remplacerRoles_echoue_surUnCompteDeLautreEtablissement() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.roles.a." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteBId = creerCompteAffecte(ETABLISSEMENT_B, "compte.roles.b." + UUID.randomUUID() + "@edukeys.tg", "DIRECTION");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(put("/api/v1/utilisateurs/" + compteBId + "/roles")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["GESTIONNAIRE"]}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void desactiver_echoue_surUnCompteDeLautreEtablissement() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.desact.a." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteBId = creerCompteAffecte(ETABLISSEMENT_B, "compte.desact.b." + UUID.randomUUID() + "@edukeys.tg", "DIRECTION");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + compteBId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void reactiver_echoue_surUnCompteDeLautreEtablissement() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.react.a." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteBId = creerCompteAffecte(ETABLISSEMENT_B, "compte.react.b." + UUID.randomUUID() + "@edukeys.tg", "DIRECTION");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(post("/api/v1/utilisateurs/" + compteBId + "/reactiver")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void motDePasseTemporaire_echoue_surUnCompteDeLautreEtablissement() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.mdp.a." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteBId = creerCompteAffecte(ETABLISSEMENT_B, "compte.mdp.b." + UUID.randomUUID() + "@edukeys.tg", "DIRECTION");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(post("/api/v1/utilisateurs/" + compteBId + "/mot-de-passe-temporaire")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void unCompteAffecteAuxDeuxEtablissements_estVisibleDepuisChacun_etSaDesactivationSurLunNeToucheParLautre() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.double.a." + UUID.randomUUID() + "@edukeys.tg");
        UUID adminBId = creerCompteAffecte(ETABLISSEMENT_B, "admin.double.b." + UUID.randomUUID() + "@edukeys.tg", "ADMIN");
        String emailPartage = "partage." + UUID.randomUUID() + "@edukeys.tg";
        UUID compteId = creerCompteAffecte(ETABLISSEMENT_A, emailPartage, "ENSEIGNANT");
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                UUID.randomUUID(), compteId, ETABLISSEMENT_B);

        String jetonAdminA = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());
        String jetonAdminB = connecter(utilisateurRepository.findById(adminBId).orElseThrow().getEmail());

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + compteId)
                        .header("Authorization", "Bearer " + jetonAdminA))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + compteId)
                        .header("Authorization", "Bearer " + jetonAdminB))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + compteId)
                        .header("Authorization", "Bearer " + jetonAdminA))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        List<Boolean> affectationBActive = jdbcTemplate.queryForList(
                "select actif from affectations_etablissement where utilisateur_id = ? and etablissement_id = ?",
                Boolean.class, compteId, ETABLISSEMENT_B);
        assertThat(affectationBActive).containsExactly(true);

        // Le compte reste actif (affectation B toujours active) : visible depuis B.
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + compteId)
                        .header("Authorization", "Bearer " + jetonAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true));
    }

    // ==================================================================
    // Droits différenciés (règle 11 : permission explicite par endpoint)
    // ==================================================================

    @Test
    void superAdmin_recoit403_surTousLesEndpointsDeGestionDeComptes() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.super.cible." + UUID.randomUUID() + "@edukeys.tg");
        String jetonSuperAdmin = connecter(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + adminAId)
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nouveau.super.%s@edukeys.tg","nomComplet":"Nouveau","roles":["GESTIONNAIRE"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/utilisateurs/" + adminAId + "/roles")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["GESTIONNAIRE"]}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/utilisateurs/" + adminAId)
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs/" + adminAId + "/reactiver")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs/" + adminAId + "/mot-de-passe-temporaire")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void direction_lit_maisNeGereNiNAttribueRien() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.direction.cible." + UUID.randomUUID() + "@edukeys.tg");
        String jetonDirecteur = connecter(EMAIL_DIRECTEUR);

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .header("Authorization", "Bearer " + jetonDirecteur))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + adminAId)
                        .header("Authorization", "Bearer " + jetonDirecteur))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/utilisateurs/" + adminAId + "/roles")
                        .header("Authorization", "Bearer " + jetonDirecteur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["GESTIONNAIRE"]}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonDirecteur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nouveau.direction.%s@edukeys.tg","nomComplet":"Nouveau","roles":["GESTIONNAIRE"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/utilisateurs/" + adminAId)
                        .header("Authorization", "Bearer " + jetonDirecteur))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs/" + adminAId + "/reactiver")
                        .header("Authorization", "Bearer " + jetonDirecteur))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs/" + adminAId + "/mot-de-passe-temporaire")
                        .header("Authorization", "Bearer " + jetonDirecteur))
                .andExpect(status().isForbidden());
    }

    @Test
    void enseignantParent_refuse403_surToutSaufMoi() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.ens.cible." + UUID.randomUUID() + "@edukeys.tg");
        String jetonEnseignant = connecter(EMAIL_ENSEIGNANT_PARENT);

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .header("Authorization", "Bearer " + jetonEnseignant))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + adminAId)
                        .header("Authorization", "Bearer " + jetonEnseignant))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonEnseignant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nouveau.ens.%s@edukeys.tg","nomComplet":"Nouveau","roles":["GESTIONNAIRE"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/utilisateurs/" + adminAId)
                        .header("Authorization", "Bearer " + jetonEnseignant))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + jetonEnseignant))
                .andExpect(status().isOk());
    }

    @Test
    void gestionnaire_refuse403_surTousLesEndpointsDeGestion() throws Exception {
        UUID compteGestionnaireId = creerCompteAffecte(ETABLISSEMENT_A, "gestionnaire." + UUID.randomUUID() + "@edukeys.tg", "GESTIONNAIRE");
        UUID adminAId = creerAdminSurEtablissementA("admin.gest.cible." + UUID.randomUUID() + "@edukeys.tg");
        String jetonGestionnaire = connecter(utilisateurRepository.findById(compteGestionnaireId).orElseThrow().getEmail());

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .header("Authorization", "Bearer " + jetonGestionnaire))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilisateurs/" + adminAId + "/reactiver")
                        .header("Authorization", "Bearer " + jetonGestionnaire))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + jetonGestionnaire))
                .andExpect(status().isOk());
    }

    // ==================================================================
    // RoleCode : périmètre de SUPER_ADMIN (test unitaire, cf. classe dédiée
    // RoleCodeTest pour l'assertion complète)
    // ==================================================================

    // ==================================================================
    // N+1 : GET /mon-etablissement doit rester à un nombre CONSTANT de
    // requêtes SQL quel que soit le nombre de comptes (CLAUDE.md, règle 10).
    // ==================================================================

    @Test
    void listeMonEtablissement_nAugmentePasLeNombreDeRequetesSql_avecLeNombreDeComptes() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.n1." + UUID.randomUUID() + "@edukeys.tg");
        for (int i = 0; i < 5; i++) {
            creerCompteAffecte(ETABLISSEMENT_A, "n1.compte5." + i + "." + UUID.randomUUID() + "@edukeys.tg",
                    "ENSEIGNANT", "PARENT");
        }
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Sans ce clear(), les Utilisateur créés ci-dessus (même contexte de
        // persistance que la requête MockMvc) restent dans le cache L1 :
        // un parcours LAZY en boucle ne déclencherait alors aucune requête
        // supplémentaire, et le test resterait vert même si le N+1 revenait
        // (revue post-implémentation, point 4).
        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());
        long requetesAvec5Comptes = stats.getPrepareStatementCount();

        for (int i = 0; i < 20; i++) {
            creerCompteAffecte(ETABLISSEMENT_A, "n1.compte25." + i + "." + UUID.randomUUID() + "@edukeys.tg",
                    "ENSEIGNANT", "PARENT");
        }

        entityManager.clear();
        stats.clear();
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());
        long requetesAvec25Comptes = stats.getPrepareStatementCount();

        assertThat(requetesAvec25Comptes)
                .withFailMessage(
                        "Le nombre de requêtes SQL doit rester constant quel que soit le nombre de comptes "
                                + "(5 comptes -> %d requêtes, 25 comptes -> %d requêtes) : un chargement LAZY en "
                                + "boucle ferait croître ce nombre linéairement (CLAUDE.md, règle 10).",
                        requetesAvec5Comptes, requetesAvec25Comptes)
                .isEqualTo(requetesAvec5Comptes);
    }

    // ==================================================================
    // Suppression du rattachement à un compte existant — revue
    // post-implémentation, 3e passe (tranchée par le donneur d'ordre) : le
    // chemin d'exploitation en deux appels identifié en 2e revue (rattacher
    // une affectation locale à un compte d'autrui via POST /utilisateurs,
    // puis récupérer son mot de passe via POST .../mot-de-passe-temporaire)
    // est fermé à la racine — POST /utilisateurs ne fait plus que créer.
    // ==================================================================

    /**
     * Preuve bout-en-bout : un ADMIN de A ne peut plus, en aucun cas,
     * atteindre un compte d'un tiers affecté ailleurs via
     * {@code POST /api/v1/utilisateurs} — ni affectation créée, ni mot de
     * passe touché. Le second appel de l'ancien chemin d'exploitation
     * ({@code POST .../mot-de-passe-temporaire}) n'a donc jamais l'occasion
     * d'être tenté (couvert séparément, en défense en profondeur, par
     * {@code motDePasseTemporaire_echoue_surUnCompteDeLautreEtablissement}).
     */
    @Test
    void creerCompte_refuse409_quandLemailAppartientAUnCompteActifDUnAutreEtablissement() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.rattachement." + UUID.randomUUID() + "@edukeys.tg");
        String emailCompteB = "compte.rattachement.b." + UUID.randomUUID() + "@edukeys.tg";
        UUID compteBId = creerCompteAffecte(ETABLISSEMENT_B, emailCompteB, "DIRECTION");
        String jetonAdminA = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","nomComplet":"Peu importe","roles":["GESTIONNAIRE"]}
                                """.formatted(emailCompteB)))
                .andExpect(status().isConflict());

        // Aucune affectation n'a été créée sur A pour ce compte.
        List<Boolean> affectationsSurA = jdbcTemplate.queryForList(
                "select actif from affectations_etablissement where utilisateur_id = ? and etablissement_id = ?",
                Boolean.class, compteBId, ETABLISSEMENT_A);
        assertThat(affectationsSurA).isEmpty();

        // Le titulaire de B garde son mot de passe d'origine : la connexion
        // avec le mot de passe de test standard fonctionne toujours.
        connecter(emailCompteB);
    }

    // ==================================================================
    // Mot de passe temporaire, garde du premier accès
    // ==================================================================

    @Test
    void creationDeCompte_renvoieLeMotDePasseTemporaireUneSeuleFois_etPermetDeSeConnecter() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.creation." + UUID.randomUUID() + "@edukeys.tg");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());
        String emailNouveauCompte = "nouveau.compte." + UUID.randomUUID() + "@edukeys.tg";

        String reponseCreation = mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","nomComplet":"Nouveau Compte","roles":["GESTIONNAIRE"]}
                                """.formatted(emailNouveauCompte)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motDePasseTemporaire").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String motDePasseTemporaire = JsonPath.read(reponseCreation, "$.motDePasseTemporaire");
        String compteId = JsonPath.read(reponseCreation, "$.compte.id");

        // Une lecture ultérieure du compte ne contient jamais le mot de passe.
        assertThat(reponseCreation).doesNotContain("motDePasseHache");
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + compteId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motDePasseAChanger").value(true));

        // Le mot de passe temporaire permet effectivement de se connecter.
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(emailNouveauCompte, motDePasseTemporaire)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jetonNouveauCompte = JsonPath.read(reponseLogin, "$.accessToken");

        // Toujours motDePasseAChanger = true côté profil, tant que non changé.
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + jetonNouveauCompte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motDePasseAChanger").value(true));
    }

    @Test
    void gardeMotDePasseAChanger_autoriseMoiEtChangementDeMotDePasse_maisRefuse403UnAutreModule() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.garde." + UUID.randomUUID() + "@edukeys.tg");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());
        String emailCompte = "garde.compte." + UUID.randomUUID() + "@edukeys.tg";

        String reponseCreation = mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","nomComplet":"Compte Garde","roles":["ADMIN"]}
                                """.formatted(emailCompte)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String motDePasseTemporaire = JsonPath.read(reponseCreation, "$.motDePasseTemporaire");

        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(emailCompte, motDePasseTemporaire)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jetonCompte = JsonPath.read(reponseLogin, "$.accessToken");

        // Autorisé : profil.
        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + jetonCompte))
                .andExpect(status().isOk());

        // Refusé : un endpoint d'un AUTRE module (etablissement), gardé par
        // ETABLISSEMENT_GERER que ce compte ADMIN porte pourtant — preuve que
        // la garde est centrale, pas recopiée module par module.
        mockMvc.perform(get("/api/v1/etablissements/courant")
                        .header("Authorization", "Bearer " + jetonCompte))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));

        // Refusé aussi dans son propre module.
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .header("Authorization", "Bearer " + jetonCompte))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));

        // Sortie de l'état : changement de mot de passe.
        mockMvc.perform(post("/api/v1/utilisateurs/moi/mot-de-passe")
                        .header("Authorization", "Bearer " + jetonCompte)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ancienMotDePasse":"%s","nouveauMotDePasse":"NouveauMotDePasse123!"}
                                """.formatted(motDePasseTemporaire)))
                .andExpect(status().isNoContent());

        // Après changement : reconnexion nécessaire (jetons révoqués), le drapeau retombe.
        String reponseReconnexion = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"NouveauMotDePasse123!"}
                                """.formatted(emailCompte)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jetonApresChangement = JsonPath.read(reponseReconnexion, "$.accessToken");

        mockMvc.perform(get("/api/v1/etablissements/courant")
                        .header("Authorization", "Bearer " + jetonApresChangement))
                .andExpect(status().isOk());
    }

    @Test
    void changerMotDePasse_revoqueLesRefreshTokensActifs_etLancienJetonDeRafraichissementNeFonctionnePlus() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.revoc." + UUID.randomUUID() + "@edukeys.tg");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());
        String emailCompte = "revoc.compte." + UUID.randomUUID() + "@edukeys.tg";

        String reponseCreation = mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","nomComplet":"Compte Revocation","roles":["GESTIONNAIRE"]}
                                """.formatted(emailCompte)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String motDePasseTemporaire = JsonPath.read(reponseCreation, "$.motDePasseTemporaire");

        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(emailCompte, motDePasseTemporaire)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jetonCompte = JsonPath.read(reponseLogin, "$.accessToken");
        String refreshTokenAvantChangement = JsonPath.read(reponseLogin, "$.refreshToken");

        mockMvc.perform(post("/api/v1/utilisateurs/moi/mot-de-passe")
                        .header("Authorization", "Bearer " + jetonCompte)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ancienMotDePasse":"%s","nouveauMotDePasse":"AutreMotDePasse123!"}
                                """.formatted(motDePasseTemporaire)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshTokenAvantChangement)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regenererMotDePasseTemporaire_invalideLeJetonPrecedent() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.regen." + UUID.randomUUID() + "@edukeys.tg");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());
        String emailCompte = "regen.compte." + UUID.randomUUID() + "@edukeys.tg";

        String reponseCreation = mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","nomComplet":"Compte Régénération","roles":["GESTIONNAIRE"]}
                                """.formatted(emailCompte)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String compteId = JsonPath.read(reponseCreation, "$.compte.id");

        entityManager.flush();
        List<Boolean> jetonsActifsAvant = jdbcTemplate.queryForList(
                "select actif from jetons_activation_compte where utilisateur_id = ?::uuid", Boolean.class, compteId);
        assertThat(jetonsActifsAvant).containsExactly(true);

        mockMvc.perform(post("/api/v1/utilisateurs/" + compteId + "/mot-de-passe-temporaire")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motDePasseTemporaire").isNotEmpty());

        entityManager.flush();
        List<Boolean> jetonsActifsApres = jdbcTemplate.queryForList(
                "select actif from jetons_activation_compte where utilisateur_id = ?::uuid order by date_creation asc",
                Boolean.class, compteId);
        // Le jeton précédent est désormais inactif, un nouveau jeton actif existe.
        assertThat(jetonsActifsApres).hasSize(2);
        assertThat(jetonsActifsApres.get(0)).isFalse();
        assertThat(jetonsActifsApres.get(1)).isTrue();
    }

    // ==================================================================
    // Désactivation logique (règle 4)
    // ==================================================================

    @Test
    void desactiver_neSupprimeRien_marqueLAffectationInactiveAvecUneDateDeDesactivation() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.logique." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteCibleId = creerCompteAffecte(ETABLISSEMENT_A, "cible.logique." + UUID.randomUUID() + "@edukeys.tg", "GESTIONNAIRE");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + compteCibleId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());

        entityManager.flush();
        List<java.util.Map<String, Object>> lignes = jdbcTemplate.queryForList(
                "select actif, date_desactivation from affectations_etablissement "
                        + "where utilisateur_id = ? and etablissement_id = ?",
                compteCibleId, ETABLISSEMENT_A);
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).get("actif")).isEqualTo(false);
        assertThat(lignes.get(0).get("date_desactivation")).isNotNull();
    }

    @Test
    void desactiver_desactiveAussiLeCompte_seulementSiAucuneAutreAffectationActive() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.compteseul." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteCibleId = creerCompteAffecte(ETABLISSEMENT_A, "cible.compteseul." + UUID.randomUUID() + "@edukeys.tg", "GESTIONNAIRE");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + compteCibleId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertThat(utilisateurRepository.findById(compteCibleId).orElseThrow().isActif()).isFalse();
    }

    @Test
    void refuseDeDesactiverSonPropreCompte() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.soimeme." + UUID.randomUUID() + "@edukeys.tg");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + adminAId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * <strong>Ne teste PAS la garde « pas de dernier ADMIN désactivé »</strong>
     * (revue post-implémentation, point 5) : {@code DELETE /api/v1/utilisateurs/{id}}
     * est réservé à {@code UTILISATEUR_GERER}, que seul {@code ADMIN} porte —
     * l'appelant HTTP est donc toujours lui-même un ADMIN actif de
     * l'établissement, distinct de la cible tant que la règle "pas son propre
     * compte" n'a pas déjà tranché en premier. Le 422 observé ici vient
     * uniquement de cette dernière règle, jamais de
     * {@code compterAutresAdminsActifs} : on ne peut pas construire, par HTTP,
     * une situation où l'appelant existe, n'est pas la cible, et où la cible
     * est pourtant le dernier ADMIN actif. Cette méthode documente donc
     * l'ordre d'évaluation (auto-désactivation avant comptage des ADMIN),
     * pas la garde elle-même — voir
     * {@code UtilisateurServiceTest#refuseDeDesactiver_quandAucunAutreAdminActif}
     * pour un test direct de cette garde, au niveau service, avec un
     * appelant distinct de la cible forgé par mock (la seule façon de
     * l'atteindre, la garde étant inatteignable par HTTP dans l'état actuel
     * du RBAC).
     */
    @Test
    void refuseDeDesactiverSonPropreCompte_memeLorsquilEstLeDernierAdminActif() throws Exception {
        UUID seulAdminId = creerAdminSurEtablissementA("admin.seul." + UUID.randomUUID() + "@edukeys.tg");
        String jetonSeulAdmin = connecter(utilisateurRepository.findById(seulAdminId).orElseThrow().getEmail());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + seulAdminId)
                        .header("Authorization", "Bearer " + jetonSeulAdmin))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reactiver_restaureLAffectation() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.reactiv." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteCibleId = creerCompteAffecte(
                ETABLISSEMENT_A, "cible.reactiv." + UUID.randomUUID() + "@edukeys.tg", "GESTIONNAIRE");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + compteCibleId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(utilisateurRepository.findById(compteCibleId).orElseThrow().isActif()).isFalse();

        mockMvc.perform(post("/api/v1/utilisateurs/" + compteCibleId + "/reactiver")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();
        List<Boolean> affectationActive = jdbcTemplate.queryForList(
                "select actif from affectations_etablissement where utilisateur_id = ? and etablissement_id = ?",
                Boolean.class, compteCibleId, ETABLISSEMENT_A);
        assertThat(affectationActive).containsExactly(true);
        entityManager.clear();
        assertThat(utilisateurRepository.findById(compteCibleId).orElseThrow().isActif()).isTrue();
    }

    /**
     * Preuve de l'index partiel {@code uk_utilisateurs_email_actif}
     * ({@code WHERE actif = true}, V3) : un email libéré par désactivation
     * est réutilisable pour un TOUT NOUVEAU compte, pendant que l'ancien
     * reste inactif — sinon l'email resterait bloqué à jamais (CLAUDE.md,
     * règle 4). Réactiver ensuite l'ANCIEN titulaire redeviendrait alors un
     * second compte actif sur le même email : refusé explicitement (défaut
     * détecté par ce test lui-même, corrigé dans
     * {@code UtilisateurService#reactiverDansEtablissementCourant}).
     */
    @Test
    void unEmailLibereParDesactivation_estReutilisable_maisReactiverLancienTitulaireEstRefuse() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.reuse." + UUID.randomUUID() + "@edukeys.tg");
        String emailCible = "cible.reuse." + UUID.randomUUID() + "@edukeys.tg";
        UUID compteCibleId = creerCompteAffecte(ETABLISSEMENT_A, emailCible, "GESTIONNAIRE");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(delete("/api/v1/utilisateurs/" + compteCibleId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(utilisateurRepository.findById(compteCibleId).orElseThrow().isActif()).isFalse();

        // L'email libéré est réutilisable pour un tout nouveau compte.
        mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","nomComplet":"Nouveau Titulaire","roles":["ENSEIGNANT"]}
                                """.formatted(emailCible)))
                .andExpect(status().isCreated());

        // Réactiver l'ANCIEN titulaire créerait un second compte actif sur le
        // même email : refusé (409), pas une erreur 500 de contrainte SQL brute.
        mockMvc.perform(post("/api/v1/utilisateurs/" + compteCibleId + "/reactiver")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    void remplacerRoles_refuseSuperAdmin_refuseSesPropresRoles_etRemplaceEffectivementLesRoles() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.remp." + UUID.randomUUID() + "@edukeys.tg");
        UUID compteCibleId = creerCompteAffecte(ETABLISSEMENT_A, "cible.remp." + UUID.randomUUID() + "@edukeys.tg", "GESTIONNAIRE");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(put("/api/v1/utilisateurs/" + compteCibleId + "/roles")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["SUPER_ADMIN"]}
                                """))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/utilisateurs/" + adminAId + "/roles")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["GESTIONNAIRE"]}
                                """))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/utilisateurs/" + compteCibleId + "/roles")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["ENSEIGNANT","PARENT"]}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + compteCibleId)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("ENSEIGNANT", "PARENT")));
    }

    @Test
    void unCompteCumuleEnseignantEtParent_sansMatriceDexclusion() throws Exception {
        UUID adminAId = creerAdminSurEtablissementA("admin.cumul." + UUID.randomUUID() + "@edukeys.tg");
        String jetonAdmin = connecter(utilisateurRepository.findById(adminAId).orElseThrow().getEmail());

        mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cumul.%s@edukeys.tg","nomComplet":"Cumul","roles":["ENSEIGNANT","PARENT"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compte.roles.length()").value(2));
    }
}
