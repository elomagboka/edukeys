package tg.novadigital.edukeys.identite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * US-08a : connexion par identifiant (email du personnel ou matricule d'un
 * compte sans email), contre PostgreSQL éphémère. Nettoyage par rollback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IdentifiantConnexionIntegrationTest {

    private static final String MOT_DE_PASSE = "Password123!";

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
                .content("""
                        {"identifiant":"%s","motDePasse":"%s"}
                        """.formatted(identifiant, motDePasse)));
    }

    private Utilisateur creerCompteSansEmail(String identifiant) {
        return utilisateurRepository.save(
                new Utilisateur(null, identifiant, passwordEncoder.encode(MOT_DE_PASSE), "Élève Test", false));
    }

    @Test
    void connexionParEmailDuPersonnel_resteInchangee() throws Exception {
        login("directeur@edukeys.tg", MOT_DE_PASSE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        // Casse et espaces normalisés, comme la clé de limitation de débit.
        login(" Directeur@Edukeys.TG ", MOT_DE_PASSE).andExpect(status().isOk());
    }

    @Test
    void connexionParIdentifiantDunCompteSansEmail_reussit() throws Exception {
        String matricule = "mat-" + UUID.randomUUID();
        creerCompteSansEmail(matricule);
        entityManager.flush();

        login(matricule.toUpperCase(), MOT_DE_PASSE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private ResultActions loginBrut(String corpsJson) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpsJson));
    }

    @Test
    void ancienChampEmail_resteAccepteCommeAlias() throws Exception {
        // Compatibilité (ADR-0004/0007) : un site statique encore à l'ancienne version envoie "email".
        loginBrut("{\"email\":\"directeur@edukeys.tg\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void ancienChampEmail_estLimiteParCompte_aussiBienQueLeNouveauChamp() throws Exception {
        String matricule = "mat-alias-" + UUID.randomUUID();
        // 4 échecs par l'alias "email" puis le 5e par "identifiant" : même compteur, casse ignorée.
        for (int i = 0; i < 4; i++) {
            loginBrut("{\"email\":\"" + (i % 2 == 0 ? matricule : matricule.toUpperCase()) + "\",\"motDePasse\":\"x\"}")
                    .andExpect(status().isUnauthorized());
        }
        login(matricule, "x").andExpect(status().isTooManyRequests());
        loginBrut("{\"email\":\"" + matricule + "\",\"motDePasse\":\"x\"}").andExpect(status().isTooManyRequests());
    }

    @Test
    void ancienChampEmailNumerique_estRejeteEn400() throws Exception {
        loginBrut("{\"email\":12345,\"motDePasse\":\"x\"}").andExpect(status().isBadRequest());
        loginBrut("{\"email\":true,\"motDePasse\":\"x\"}").andExpect(status().isBadRequest());
    }

    @Test
    void siLesDeuxChampsSontPresents_identifiantPrime_dansLeDtoComme_dansLeFiltre() throws Exception {
        String matricule = "mat-both-" + UUID.randomUUID();
        creerCompteSansEmail(matricule);
        entityManager.flush();

        // identifiant valide + email bidon : authentifié par identifiant (ordre des champs sans effet).
        loginBrut("{\"email\":\"inconnu@edukeys.tg\",\"identifiant\":\"" + matricule + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                .andExpect(status().isOk());
        loginBrut("{\"identifiant\":\"" + matricule + "\",\"email\":\"inconnu@edukeys.tg\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                .andExpect(status().isOk());

        // identifiant faux + email valide : échec, compté contre l'IDENTIFIANT (clé du filtre) et jamais contre l'email.
        String autre = "mat-both2-" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            loginBrut("{\"email\":\"" + matricule + "\",\"identifiant\":\"" + autre + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                    .andExpect(status().isUnauthorized());
        }
        login(autre, "x").andExpect(status().isTooManyRequests());
        login(matricule, MOT_DE_PASSE).andExpect(status().isOk());

        // identifiant non textuel + email valide : 400, jamais de repli silencieux sur l'email.
        loginBrut("{\"identifiant\":12345,\"email\":\"" + matricule + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void messageIdentique_pourIdentifiantInconnuEtMauvaisMotDePasse() throws Exception {
        String matricule = "mat-" + UUID.randomUUID();
        creerCompteSansEmail(matricule);
        entityManager.flush();

        String inconnu = login("inconnu-" + UUID.randomUUID(), "x").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String mauvais = login(matricule, "x").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(inconnu, "$.detail")).isEqualTo(JsonPath.read(mauvais, "$.detail"));
    }

    @Test
    void identifiantUniqueEntreComptesActifs_refuseLeDoublonEnBase() {
        String identifiant = "mat-dup-" + UUID.randomUUID();
        creerCompteSansEmail(identifiant);
        entityManager.flush();

        // L'identifiant est normalisé : une variante de casse est bien un doublon.
        creerCompteSansEmail(identifiant.toUpperCase());
        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(Exception.class);
    }

    @Test
    void identifiantLibereParDesactivation_estReutilisable() {
        String identifiant = "mat-reuse-" + UUID.randomUUID();
        Utilisateur premier = creerCompteSansEmail(identifiant);
        entityManager.flush();
        premier.desactiver();
        utilisateurRepository.save(premier);
        entityManager.flush();

        creerCompteSansEmail(identifiant);
        entityManager.flush();

        assertThat(utilisateurRepository.existsByIdentifiantConnexionAndActifTrue(identifiant)).isTrue();
    }

    @Test
    void limitationDeDebit_parIdentifiantNormalise() throws Exception {
        String matricule = "mat-rl-" + UUID.randomUUID();
        // Seuil par compte : 3 tolérés, le 4e échec déclenche l'attente ; la clé ignore la casse.
        for (int i = 0; i < 4; i++) {
            String saisie = i % 2 == 0 ? matricule : matricule.toUpperCase();
            login(saisie, "mauvais").andExpect(status().isUnauthorized());
        }
        login(matricule.toUpperCase(), "mauvais").andExpect(status().isTooManyRequests());
        // Un autre identifiant, même IP, n'est pas bloqué par le compteur par compte.
        login("autre-" + UUID.randomUUID(), "mauvais").andExpect(status().isUnauthorized());
    }

    @Test
    void refuseEleveEtParent_surLaCreationDeCompte_avec422() throws Exception {
        String emailAdmin = "admin.roles." + UUID.randomUUID() + "@edukeys.tg";
        Utilisateur admin = utilisateurRepository.save(
                new Utilisateur(emailAdmin, passwordEncoder.encode(MOT_DE_PASSE), "Admin Test", false));
        entityManager.flush();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, '01977000-0000-7000-9000-000000000001', true, now(), now())",
                affectationId, admin.getId());
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);
        String jeton = JsonPath.read(login(emailAdmin, MOT_DE_PASSE).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        for (String role : new String[] {"ELEVE", "PARENT"}) {
            mockMvc.perform(post("/api/v1/utilisateurs")
                            .header("Authorization", "Bearer " + jeton)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"x.%s@edukeys.tg","nomComplet":"X","roles":["%s"]}
                                    """.formatted(UUID.randomUUID(), role)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ROLE_NON_ATTRIBUABLE_MANUELLEMENT"));
        }
    }

    @Test
    void motDePasseTemporaireAvecExpirationExplicite_expireALaDateDemandee() throws Exception {
        String matricule = "mat-exp-" + UUID.randomUUID();
        Utilisateur eleve = creerCompteSansEmail(matricule);
        entityManager.flush();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, '01977000-0000-7000-9000-000000000001', true, now(), now())",
                affectationId, eleve.getId());
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ELEVE')", affectationId);

        Instant expiration = Instant.now().plus(Duration.ofDays(45));
        String temporaire;
        try (var portee = tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement.ouvrir(
                UUID.fromString("01977000-0000-7000-9000-000000000001"))) {
            temporaire = utilisateurService.emettreMotDePasseTemporaireAvecExpiration(eleve.getId(), expiration);
            entityManager.flush();
        }

        Instant enBase = jdbcTemplate.queryForObject(
                "select date_expiration from jetons_activation_compte where utilisateur_id = ? and actif = true",
                java.sql.Timestamp.class, eleve.getId()).toInstant();
        assertThat(enBase).isCloseTo(expiration, within(1, ChronoUnit.MILLIS));

        login(matricule, temporaire).andExpect(status().isOk());

        // Le jeton expire : la même connexion est refusée avec le code dédié.
        jdbcTemplate.update(
                "update jetons_activation_compte set date_expiration = now() - interval '1 second' where utilisateur_id = ?",
                eleve.getId());
        login(matricule, temporaire)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_TEMPORAIRE_EXPIRE"));
    }
}
