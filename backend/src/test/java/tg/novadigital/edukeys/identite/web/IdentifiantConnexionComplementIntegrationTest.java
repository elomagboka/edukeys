package tg.novadigital.edukeys.identite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import tg.novadigital.edukeys.identite.service.UtilisateurService;

/**
 * US-08a, compléments de {@link IdentifiantConnexionIntegrationTest} : données
 * de démo antérieures à V15, limitation de débit d'un compte sans email,
 * réactivation, remplacement de rôles, lecture de comptes sans email,
 * expiration explicite dans le passé. Nettoyage par rollback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdentifiantConnexionComplementIntegrationTest {

    private static final String MOT_DE_PASSE = "Password123!";
    private static final String ETABLISSEMENT_A = "01977000-0000-7000-9000-000000000001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private UtilisateurService utilisateurService;
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

    private ResultActions login(String identifiant, String motDePasse) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\"" + motDePasse + "\"}"));
    }

    private String jeton(String identifiant) throws Exception {
        return JsonPath.read(login(identifiant, MOT_DE_PASSE).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private Utilisateur creerCompteSansEmail(String identifiant) {
        return utilisateurRepository.save(
                new Utilisateur(null, identifiant, passwordEncoder.encode(MOT_DE_PASSE), "Élève Test", false));
    }

    /** Compte + affectation active sur l'établissement A, rôles donnés, inséré en base (email nullable). */
    private Utilisateur creerCompteAffecte(String email, String identifiant, String... roles) {
        Utilisateur compte = utilisateurRepository.save(
                new Utilisateur(email, identifiant, passwordEncoder.encode(MOT_DE_PASSE), "Compte Test", false));
        entityManager.flush();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), ETABLISSEMENT_A);
        for (String role : roles) {
            jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, role);
        }
        return compte;
    }

    private String creerAdmin() throws Exception {
        String email = "admin.08a." + UUID.randomUUID() + "@edukeys.tg";
        creerCompteAffecte(email, email, "ADMIN");
        return jeton(email);
    }

    @Test
    void comptesDeDemoAnterieursAV15_ontUnIdentifiantEgalAuEmail_etSeConnectentParEmail() throws Exception {
        Long incoherents = jdbcTemplate.queryForObject(
                "select count(*) from utilisateurs where email is not null and identifiant_connexion <> lower(btrim(email))",
                Long.class);
        assertThat(incoherents).isZero();
        for (String email : new String[] {"directeur@edukeys.tg", "super.admin@edukeys.tg", "enseignant.parent@edukeys.tg"}) {
            login(email, MOT_DE_PASSE).andExpect(status().isOk());
        }
    }

    @Test
    void limitationDeDebit_unCompteSansEmailExistantEstLimite_quelleQueSoitLaCasse() throws Exception {
        String matricule = "mat-rl2-" + UUID.randomUUID();
        creerCompteSansEmail(matricule);
        entityManager.flush();
        for (int i = 0; i < 4; i++) {
            login(i % 2 == 0 ? matricule : matricule.toUpperCase(), "mauvais").andExpect(status().isUnauthorized());
        }
        // Même le bon mot de passe est refusé pendant l'attente : le compteur porte bien sur l'identifiant.
        login(" " + matricule.toUpperCase() + " ", MOT_DE_PASSE).andExpect(status().isTooManyRequests());
    }

    @Test
    void reactivationRefusee_quandUnAutreCompteActifSansEmailPorteDejaLIdentifiant() throws Exception {
        String jetonAdmin = creerAdmin();
        String identifiant = "mat-react-" + UUID.randomUUID();
        Utilisateur ancien = creerCompteAffecte(null, identifiant, "ENSEIGNANT");

        mockMvc.perform(delete("/api/v1/utilisateurs/" + ancien.getId()).header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();
        // L'identifiant libéré est repris par un autre compte actif.
        creerCompteSansEmail(identifiant);
        entityManager.flush();

        mockMvc.perform(post("/api/v1/utilisateurs/" + ancien.getId() + "/reactiver")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_IDENTIFIANT_DUPLIQUE"));
    }

    @Test
    void reactivationReussit_pourUnCompteSansEmail_quandIdentifiantToujoursLibre() throws Exception {
        String jetonAdmin = creerAdmin();
        Utilisateur compte = creerCompteAffecte(null, "mat-ok-" + UUID.randomUUID(), "ENSEIGNANT");

        mockMvc.perform(delete("/api/v1/utilisateurs/" + compte.getId()).header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();
        mockMvc.perform(post("/api/v1/utilisateurs/" + compte.getId() + "/reactiver")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void remplacerRoles_refuseEleveEtParent_avec422() throws Exception {
        String jetonAdmin = creerAdmin();
        String emailCible = "cible.remp08a." + UUID.randomUUID() + "@edukeys.tg";
        Utilisateur cible = creerCompteAffecte(emailCible, emailCible, "GESTIONNAIRE");

        for (String role : new String[] {"ELEVE", "PARENT"}) {
            mockMvc.perform(put("/api/v1/utilisateurs/" + cible.getId() + "/roles")
                            .header("Authorization", "Bearer " + jetonAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roles\":[\"ENSEIGNANT\",\"" + role + "\"]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ROLE_NON_ATTRIBUABLE_MANUELLEMENT"));
        }
    }

    @Test
    void profilEtListes_neplantentPas_surUnCompteSansEmail() throws Exception {
        String matricule = "mat-moi-" + UUID.randomUUID();
        creerCompteAffecte(null, matricule, "ENSEIGNANT");
        String jetonAdmin = creerAdmin();

        mockMvc.perform(get("/api/v1/utilisateurs/moi").header("Authorization", "Bearer " + jeton(matricule)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifiantConnexion").value(matricule));

        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement?size=200")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[?(@.identifiantConnexion=='" + matricule + "')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/utilisateurs?size=200&sort=email,asc")
                        .header("Authorization", "Bearer " + jeton("super.admin@edukeys.tg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").isArray());
    }

    @Test
    void emettreMotDePasseTemporaire_refuseUneExpirationDansLePasseOuNulle_etNeChangeRien() {
        Utilisateur eleve = creerCompteAffecte(null, "mat-past-" + UUID.randomUUID(), "ELEVE");
        entityManager.flush();

        try (var portee = tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement.ouvrir(UUID.fromString(ETABLISSEMENT_A))) {
            assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(
                    eleve.getId(), Instant.now().minusSeconds(60))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(eleve.getId(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(passwordEncoder.matches(MOT_DE_PASSE,
                utilisateurRepository.findById(eleve.getId()).orElseThrow().getMotDePasseHache())).isTrue();
    }
}
