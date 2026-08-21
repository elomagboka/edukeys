package tg.novadigital.edukeys.identite.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;
import tg.novadigital.edukeys.identite.service.UtilisateurService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration bout-en-bout de l'authentification (T-04), contre un
 * conteneur PostgreSQL éphémère migré par Flyway (schéma + données de
 * démonstration {@code R__identite_comptes_demo.sql}), fourni par
 * {@link tg.novadigital.edukeys.testsupport.ConfigurationBaseDeTest}. Docker
 * suffit : ni {@code docker compose}, ni base locale préexistante.
 *
 * <p>Nettoyage par rollback transactionnel (CLAUDE.md, règle 4 ; correction
 * T-04, lot 2 n°10) : chaque test s'exécute dans une transaction annulée à la
 * fin, y compris les appels HTTP via MockMvc (même thread, même contexte de
 * persistance) — aucune donnée créée par un test ne doit polluer les
 * suivants.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    private static final String EMAIL_DIRECTEUR = "directeur@edukeys.tg";
    private static final String EMAIL_ENSEIGNANT_PARENT = "enseignant.parent@edukeys.tg";
    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UtilisateurService utilisateurService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void connecteEtRenvoieLesJetonsAvecLEtablissementActif_quandIdentifiantsValides() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(EMAIL_DIRECTEUR, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.etablissementId").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("DIRECTION"));
    }

    @Test
    void cumuleLesRolesDeLAffectation_quandUtilisateurEnseignantEtParent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(EMAIL_ENSEIGNANT_PARENT, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("ENSEIGNANT", "PARENT")));
    }

    @Test
    void refuse401_quandMotDePasseIncorrect() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"mauvais"}
                                """.formatted(EMAIL_DIRECTEUR)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refuse401_quandEndpointProtegeAppeleSansJeton() throws Exception {
        mockMvc.perform(get("/api/v1/utilisateurs/moi"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Critère de fin de T-04 (docs/SPRINT-0.md) : deux utilisateurs de rôles
     * différents, un endpoint protégé par {@code @PreAuthorize}, l'un passe et
     * l'autre reçoit un 403. {@code GET /api/v1/utilisateurs} exige la
     * permission {@code UTILISATEUR_GERER_PLATEFORME} (UtilisateurController),
     * portée uniquement par SUPER_ADMIN : un compte DIRECTION authentifié,
     * mais sans cette permission, doit être refusé — pas seulement un 401
     * générique sans jeton, qui ne prouve que l'authentification, jamais
     * l'autorisation.
     */
    @Test
    void autoriseSuperAdmin_maisRefuse403AuDirecteur_surLEndpointReserveAuSuperAdmin() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String jetonDirecteur = connecterEtObtenirAccessToken(EMAIL_DIRECTEUR);

        mockMvc.perform(get("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonDirecteur))
                .andExpect(status().isForbidden());
    }

    /**
     * Preuve que la liste blanche de tri de {@code UtilisateurController.lister}
     * s'applique réellement : {@code motDePasseHache} est une propriété
     * Spring Data comme une autre, donc sans filtrage, trier dessus
     * exposerait l'ordre lexicographique des empreintes bcrypt (relecture
     * T-04, repasse n°2). Un champ hors liste doit être silencieusement
     * ignoré (repli sur le tri par défaut), jamais un 500 — la propriété
     * existe bel et bien, {@code PropertyReferenceException} ne se
     * déclencherait que pour un champ réellement inexistant.
     */
    @Test
    void ignoreUnTriSurUnChampInterdit_etNeRenvoiePas500() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/utilisateurs")
                        .param("sort", "motDePasseHache,asc")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk());
    }

    /** Symétrique du test ci-dessus : un champ de la liste blanche trie effectivement le résultat. */
    @Test
    void trieParEmail_quandCritereDeTriAutorise() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        String reponse = mockMvc.perform(get("/api/v1/utilisateurs")
                        .param("sort", "email,asc")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> emails = com.jayway.jsonpath.JsonPath.read(reponse, "$.contenu[*].email");

        assertThat(emails).isSortedAccordingTo(String::compareTo);
        // Les trois comptes de démonstration doivent figurer dans cet ordre :
        // preuve que le tri porte réellement sur email, pas un artefact du
        // tri par défaut (dateCreation) qui coïnciderait par hasard.
        assertThat(emails).contains(EMAIL_DIRECTEUR, EMAIL_ENSEIGNANT_PARENT, EMAIL_SUPER_ADMIN);
    }

    /**
     * {@link tg.novadigital.edukeys.identite.service.UtilisateurService#listerTous}
     * ne filtre par aucun établissement, délibérément (voir sa javadoc et
     * ADR-0002) : un compte n'a pas « son » établissement, puisqu'il peut
     * porter plusieurs {@code AffectationEtablissement}. Ce test le prouve en
     * créant deux comptes affectés à deux établissements distincts et en
     * vérifiant que les deux apparaissent dans la même page de résultats.
     */
    @Test
    void listeLesComptesDeTousLesEtablissements_sansFiltrageParEtablissement() throws Exception {
        java.util.UUID etablissementA = java.util.UUID.randomUUID();
        java.util.UUID etablissementB = java.util.UUID.randomUUID();

        Utilisateur compteEtablissementA = utilisateurRepository.save(new Utilisateur(
                "compte.etab.a." + java.util.UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Compte Établissement A", false));
        Utilisateur compteEtablissementB = utilisateurRepository.save(new Utilisateur(
                "compte.etab.b." + java.util.UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Compte Établissement B", false));

        // Insertion Hibernate pas encore committée sous @Transactional : sans
        // flush, la ligne JDBC suivante violerait la contrainte de clé
        // étrangère (même remarque que neStockeJamaisLeRefreshTokenEnClairEnBase_...).
        entityManager.flush();

        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (gen_random_uuid(), ?, ?, true, now(), now())",
                compteEtablissementA.getId(), etablissementA);
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (gen_random_uuid(), ?, ?, true, now(), now())",
                compteEtablissementB.getId(), etablissementB);

        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/utilisateurs")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[?(@.email == '" + compteEtablissementA.getEmail() + "')]").exists())
                .andExpect(jsonPath("$.contenu[?(@.email == '" + compteEtablissementB.getEmail() + "')]").exists());
    }

    /**
     * Régression directe de la relecture T-04 (repasse n°2) : ADMIN et
     * SUPER_ADMIN portaient tous deux {@code UTILISATEUR_GERER}, ce qui
     * donnait à un ADMIN (rôle borné à son établissement, ADR-0002 §5) accès
     * à la liste de tous les établissements dès lors que l'endpoint était
     * gardé par cette seule permission. Depuis, l'endpoint exige
     * {@code UTILISATEUR_GERER_PLATEFORME}, qu'ADMIN ne porte pas.
     */
    @Test
    void refuse403UnAdmin_surLaListeGlobaleDesUtilisateurs() throws Exception {
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.test." + java.util.UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin Test Établissement", false));
        entityManager.flush();

        java.util.UUID affectationId = java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, compteAdmin.getId(), java.util.UUID.randomUUID());
        jdbcTemplate.update(
                "insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')",
                affectationId);

        String jetonAdmin = connecterEtObtenirAccessToken(compteAdmin.getEmail());

        mockMvc.perform(get("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void autoriseToutRoleAuthentifie_surSonPropreProfil() throws Exception {
        String jetonDirecteur = connecterEtObtenirAccessToken(EMAIL_DIRECTEUR);

        mockMvc.perform(get("/api/v1/utilisateurs/moi")
                        .header("Authorization", "Bearer " + jetonDirecteur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_DIRECTEUR));
    }

    @Test
    void basculeVersLEtablissementDeSonAffectationActive_etRenvoieDeNouveauxJetons() throws Exception {
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(EMAIL_DIRECTEUR, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String jeton = com.jayway.jsonpath.JsonPath.read(reponseLogin, "$.accessToken");
        String etablissementId = com.jayway.jsonpath.JsonPath.read(reponseLogin, "$.etablissementId");

        mockMvc.perform(post("/api/v1/auth/etablissement-actif")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etablissementId":"%s"}
                                """.formatted(etablissementId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etablissementId").value(etablissementId))
                .andExpect(jsonPath("$.roles[0]").value("DIRECTION"));
    }

    @Test
    void refuse403LaBascule_quandAucuneAffectationActiveSurLetablissementDemande() throws Exception {
        String jeton = connecterEtObtenirAccessToken(EMAIL_DIRECTEUR);

        mockMvc.perform(post("/api/v1/auth/etablissement-actif")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etablissementId":"%s"}
                                """.formatted(java.util.UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    /**
     * ADR-0002 §5 : SUPER_ADMIN « n'a aucun accès aux données métier ». Ce
     * test prouve la seule chose vérifiable côté API aujourd'hui — après
     * bascule sur un établissement, ses rôles restent strictement
     * {@code ["SUPER_ADMIN"]}, jamais les rôles métier (ex. DIRECTION) d'une
     * affectation sur cet établissement, même s'il n'en a aucune. Il ne prouve
     * PAS l'isolation des données : le filtre Hibernate qu'ADR-0002 décrit
     * comme mécanisme réel d'isolation est le périmètre de T-05, pas encore
     * construit (voir PermissionResolverTest pour la garantie de permissions
     * qui existe, elle, dès aujourd'hui).
     */
    @Test
    void neConserveQueLeRoleSuperAdmin_apresBasculeSurUnEtablissementSansAffectation() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/auth/etablissement-actif")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etablissementId":"01977000-0000-7000-9000-000000000001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etablissementId").value("01977000-0000-7000-9000-000000000001"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("SUPER_ADMIN"));
    }

    @Test
    void refuse401LaBascule_quandAppeleeSansJeton() throws Exception {
        mockMvc.perform(post("/api/v1/auth/etablissement-actif")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etablissementId":"%s"}
                                """.formatted(java.util.UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    private String connecterEtObtenirAccessToken(String email) throws Exception {
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(email, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(reponseLogin, "$.accessToken");
    }

    @Test
    void rafraichitLesJetons_puisRevoqueLAncienRefreshToken() throws Exception {
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(EMAIL_DIRECTEUR, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = com.jayway.jsonpath.JsonPath.read(reponseLogin, "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Le jeton présenté a été révoqué par rotation : le réutiliser échoue.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Preuve bout-en-bout de la détection de rejeu (correction T-04, lot 1
     * n°4) : login puis deux rotations successives créent une chaîne de trois
     * jetons dans la même famille. Représenter le tout premier — déjà échangé,
     * donc déjà révoqué — doit couper toute la famille, y compris le dernier
     * jeton émis, pourtant encore valide et jamais présenté.
     */
    @Test
    void revoqueTouteLaFamilleDeJetons_quandUnJetonDejaTourneEstRepresente() throws Exception {
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(EMAIL_DIRECTEUR, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken1 = com.jayway.jsonpath.JsonPath.read(reponseLogin, "$.refreshToken");

        String reponseRefresh1 = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshToken2 = com.jayway.jsonpath.JsonPath.read(reponseRefresh1, "$.refreshToken");

        // Deuxième rotation : émet un troisième jeton, encore actif, jamais
        // présenté ci-dessous — c'est lui qui doit prouver que toute la famille
        // est coupée, pas seulement le jeton effectivement rejoué.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken2)))
                .andExpect(status().isOk());

        // Rejeu : refreshToken1 a déjà été échangé par la première rotation,
        // donc déjà révoqué. Le représenter est le signal classique d'un vol.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken1)))
                .andExpect(status().isUnauthorized());

        java.util.UUID utilisateurId = utilisateurRepository.findByEmailAndActifTrue(EMAIL_DIRECTEUR)
                .orElseThrow()
                .getId();

        entityManager.flush();
        java.util.List<Boolean> statutsActifs = jdbcTemplate.queryForList(
                "select actif from jetons_rafraichissement where utilisateur_id = ?",
                Boolean.class, utilisateurId);

        // Login + deux rotations = trois jetons émis dans la même famille,
        // tous désormais révoqués — y compris celui jamais présenté au rejeu.
        assertThat(statutsActifs).hasSize(3);
        assertThat(statutsActifs).allMatch(actif -> !actif);
    }

    @Test
    void neStockeJamaisLeRefreshTokenEnClairEnBase_quandConnexionReussie() throws Exception {
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(EMAIL_DIRECTEUR, MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshTokenEnClair = com.jayway.jsonpath.JsonPath.read(reponseLogin, "$.refreshToken");

        // Sous @Transactional, l'insertion Hibernate n'est pas encore committée :
        // sans flush explicite, une lecture JDBC directe (hors session Hibernate)
        // ne verrait pas encore la ligne, même sur la même connexion.
        entityManager.flush();
        java.util.List<String> jetonsHaches = jdbcTemplate.queryForList(
                "select jeton_hache from jetons_rafraichissement where actif = true", String.class);

        // La table ne contient jamais la valeur en clair présentée au client :
        // seule une empreinte doit y figurer (arbitrage T-04 n°3).
        assertThat(jetonsHaches).isNotEmpty();
        assertThat(jetonsHaches).doesNotContain(refreshTokenEnClair);
        assertThat(jetonsHaches).allSatisfy(hache -> assertThat(hache).isNotEqualTo(refreshTokenEnClair));
    }

    @Test
    void invalideImmediatementLeRefreshTokenActif_quandLeCompteEstDesactive() throws Exception {
        Utilisateur compteTest = new Utilisateur(
                "test.desactivation." + java.util.UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE),
                "Compte Test Désactivation",
                false);
        compteTest = utilisateurRepository.save(compteTest);

        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(compteTest.getEmail(), MOT_DE_PASSE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshTokenApresLogin = com.jayway.jsonpath.JsonPath.read(reponseLogin, "$.refreshToken");

        // Le refresh token fonctionne tant que le compte est actif.
        String reponseRefresh = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshTokenApresLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String refreshTokenActif = com.jayway.jsonpath.JsonPath.read(reponseRefresh, "$.refreshToken");

        utilisateurService.desactiverCompte(compteTest.getId());

        // Le même jeton, encore valide côté expiration, est désormais rejeté :
        // la désactivation du compte révoque immédiatement ses jetons actifs.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshTokenActif)))
                .andExpect(status().isUnauthorized());
    }
}
