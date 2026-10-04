package tg.novadigital.edukeys.identite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.common.securite.limitation.FiltreLimitationDebit;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Corrections de revue US-08a : identifiant JSON non textuel (M1), remplacement
 * de rôles qui conserve ELEVE/PARENT (M4), liste plateforme sans comptes
 * élèves (M5). Nettoyage par rollback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RevueUs08aIntegrationTest {

    private static final String MOT_DE_PASSE = "Password123!";
    private static final String ETABLISSEMENT_A = "01977000-0000-7000-9000-000000000001";
    private static final String COMPTE_DEMO_ENSEIGNANT_PARENT = "01977000-0000-7000-8000-000000000203";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FiltreLimitationDebit filtreLimitationDebit;
    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void reinitialiser() {
        filtreLimitationDebit.reinitialiserPourLesTests();
    }

    private ResultActions loginBrut(String corpsJson) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpsJson));
    }

    private String jeton(String identifiant) throws Exception {
        return JsonPath.read(loginBrut("{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private UUID creerCompteAffecte(String email, String identifiant, String role) {
        Utilisateur compte = utilisateurRepository.save(
                new Utilisateur(email, identifiant, passwordEncoder.encode(MOT_DE_PASSE), "Compte " + identifiant, false));
        entityManager.flush();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())", affectationId, compte.getId(), ETABLISSEMENT_A);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, role);
        return compte.getId();
    }

    private String jetonAdmin() throws Exception {
        String email = "admin.revue." + UUID.randomUUID() + "@edukeys.tg";
        creerCompteAffecte(email, email, "ADMIN");
        return jeton(email);
    }

    // ---- M1

    @Test
    void identifiantNonTextuel_estRejeteEn400_etNAuthentifieJamais() throws Exception {
        // Un matricule purement numérique serait sinon converti en chaîne par Jackson alors que
        // la limitation par compte ne lit que les chaînes : la clé et la valeur authentifiée divergeraient.
        creerCompteAffecte(null, "12345", "ENSEIGNANT");
        entityManager.flush();

        for (String valeur : new String[] {"12345", "1.5", "true", "[\"a\"]", "{\"a\":1}"}) {
            loginBrut("{\"identifiant\":" + valeur + ",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                    .andExpect(status().isBadRequest());
        }
        // La même valeur, textuelle, reste acceptée.
        loginBrut("{\"identifiant\":\"12345\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}").andExpect(status().isOk());
    }

    @Test
    void identifiantVideOuTropLong_estRejeteEn400() throws Exception {
        loginBrut("{\"identifiant\":\"\",\"motDePasse\":\"x\"}").andExpect(status().isBadRequest());
        loginBrut("{\"identifiant\":\"" + "a".repeat(256) + "\",\"motDePasse\":\"x\"}").andExpect(status().isBadRequest());
    }

    // ---- M4

    @Test
    void remplacerRoles_surLeCompteDemoEnseignantParent_conserveParent() throws Exception {
        String jetonAdmin = jetonAdmin();

        // {ENSEIGNANT, PARENT} renvoyé tel quel : accepté (PARENT n'est pas AJOUTÉ).
        mockMvc.perform(put("/api/v1/utilisateurs/" + COMPTE_DEMO_ENSEIGNANT_PARENT + "/roles")
                        .header("Authorization", "Bearer " + jetonAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ENSEIGNANT\",\"PARENT\"]}"))
                .andExpect(status().isNoContent());

        // {ENSEIGNANT} seul : PARENT est conservé automatiquement.
        mockMvc.perform(put("/api/v1/utilisateurs/" + COMPTE_DEMO_ENSEIGNANT_PARENT + "/roles")
                        .header("Authorization", "Bearer " + jetonAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ENSEIGNANT\"]}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement/" + COMPTE_DEMO_ENSEIGNANT_PARENT)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("ENSEIGNANT", "PARENT")));
    }

    @Test
    void remplacerRoles_refuseLAjoutDunRoleDuPersonnelSurUnCompteEleve() throws Exception {
        String jetonAdmin = jetonAdmin();
        UUID eleve = creerCompteAffecte(null, "mat-promo-" + UUID.randomUUID(), "ELEVE");

        mockMvc.perform(put("/api/v1/utilisateurs/" + eleve + "/roles")
                        .header("Authorization", "Bearer " + jetonAdmin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ELEVE\",\"GESTIONNAIRE\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_NON_ATTRIBUABLE_MANUELLEMENT"));
    }

    // ---- M5

    @Test
    void listePlateforme_excluLesComptesEleve_etGardeLEnseignantParent() throws Exception {
        String jetonSuperAdmin = jeton("super.admin@edukeys.tg");
        String reponseAvant = mockMvc.perform(get("/api/v1/utilisateurs?size=200")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int totalAvant = JsonPath.read(reponseAvant, "$.totalElements");

        String matricule = "mat-liste-" + UUID.randomUUID();
        creerCompteAffecte(null, matricule, "ELEVE");
        creerCompteAffecte(null, "mat-parent-" + UUID.randomUUID(), "PARENT");
        entityManager.flush();

        String reponse = mockMvc.perform(get("/api/v1/utilisateurs?size=200")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[?(@.identifiantConnexion=='" + matricule + "')]").isEmpty())
                .andExpect(jsonPath("$.contenu[?(@.email=='enseignant.parent@edukeys.tg')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        // Le comptage de pagination exclut aussi les comptes écartés.
        assertThat((Integer) JsonPath.read(reponse, "$.totalElements")).isEqualTo(totalAvant);
    }

    @Test
    void listePlateforme_n_affichePlusUnAncienEnseignantDevenuSeulementParent() throws Exception {
        String jetonSuperAdmin = jeton("super.admin@edukeys.tg");
        String email = "ex-ens-" + UUID.randomUUID() + "@edukeys.tg";
        Utilisateur compte = utilisateurRepository.save(
                new Utilisateur(email, email, passwordEncoder.encode(MOT_DE_PASSE), "Ancien enseignant", false));
        entityManager.flush();
        // Affectation ENSEIGNANT désactivée (A) + affectation PARENT active (B) : seul PARENT reste actif.
        UUID ancienne = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_desactivation, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, false, now(), now(), now())", ancienne, compte.getId(), ETABLISSEMENT_A);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ENSEIGNANT')", ancienne);
        UUID courante = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, '01977000-0000-7000-9000-000000000002'::uuid, true, now(), now())", courante, compte.getId());
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'PARENT')", courante);

        mockMvc.perform(get("/api/v1/utilisateurs?size=200").header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[?(@.email=='" + email + "')]").isEmpty());
    }

    @Test
    void motDePasseVideOuTropLong_estRejeteEn400() throws Exception {
        loginBrut("{\"identifiant\":\"a\",\"motDePasse\":\"\"}").andExpect(status().isBadRequest());
        loginBrut("{\"identifiant\":\"a\",\"motDePasse\":\"" + "x".repeat(256) + "\"}").andExpect(status().isBadRequest());
    }
}
