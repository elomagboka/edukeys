package tg.novadigital.edukeys.identite.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.securite.limitation.FiltreLimitationDebit;
import tg.novadigital.edukeys.identite.EmetteurMotDePasseTemporaire;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Compléments de revue US-08a : liste plateforme (pagination exacte, pas de N+1)
 * et corrélation de bout en bout des empreintes compte_inconnu / limitation_debit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RevueUs08aComplementsIntegrationTest {

    private static final String MOT_DE_PASSE = "Password123!";
    private static final String ETABLISSEMENT_A = "01977000-0000-7000-9000-000000000001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EmetteurMotDePasseTemporaire emetteur;
    @Autowired
    private FiltreLimitationDebit filtreLimitationDebit;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @PersistenceContext
    private EntityManager entityManager;

    private Logger loggerSecurite;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void preparer() {
        filtreLimitationDebit.reinitialiserPourLesTests();
        loggerSecurite = (Logger) LoggerFactory.getLogger("SECURITE");
        appender = new ListAppender<>();
        appender.start();
        loggerSecurite.addAppender(appender);
    }

    @AfterEach
    void nettoyer() {
        loggerSecurite.detachAppender(appender);
    }

    private void creerCompteAffecte(String email, String identifiant, String... roles) {
        Utilisateur compte = utilisateurRepository.save(
                new Utilisateur(email, identifiant, passwordEncoder.encode(MOT_DE_PASSE), "Compte " + identifiant, false));
        entityManager.flush();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())", affectationId, compte.getId(), ETABLISSEMENT_A);
        for (String role : roles) {
            jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, role);
        }
    }

    private String jetonSuperAdmin() throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifiant\":\"super.admin@edukeys.tg\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private String lister(String jeton, int page, int size) throws Exception {
        return mockMvc.perform(get("/api/v1/utilisateurs?page=" + page + "&size=" + size)
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void listePlateforme_paginationExacte_sansCompteEleveNiParentSeul() throws Exception {
        String jeton = jetonSuperAdmin();
        List<String> intrus = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String eleve = "mat-pag-e-" + i + "-" + UUID.randomUUID();
            String parent = "mat-pag-p-" + i + "-" + UUID.randomUUID();
            creerCompteAffecte(null, eleve, "ELEVE");
            creerCompteAffecte(null, parent, "PARENT");
            intrus.add(eleve);
            intrus.add(parent);
        }
        entityManager.flush();

        int size = 2;
        int total = JsonPath.read(lister(jeton, 0, size), "$.totalElements");
        int pages = (total + size - 1) / size;
        List<String> vus = new ArrayList<>();
        for (int p = 0; p < pages; p++) {
            String reponse = lister(jeton, p, size);
            List<String> ids = JsonPath.read(reponse, "$.contenu[*].id");
            assertThat(ids).as("page " + p).hasSize(p < pages - 1 ? size : total - size * (pages - 1));
            vus.addAll(ids);
            List<String> identifiants = JsonPath.read(reponse, "$.contenu[*].identifiantConnexion");
            assertThat(identifiants).doesNotContainAnyElementsOf(intrus);
        }
        assertThat(vus).hasSize(total).doesNotHaveDuplicates();
    }

    @Test
    void listePlateforme_nAugmentePasLeNombreDeRequetesSql_avecLeNombreDeComptes() throws Exception {
        String jeton = jetonSuperAdmin();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (int i = 0; i < 5; i++) {
            creerCompteAffecte("n1p5." + i + "." + UUID.randomUUID() + "@edukeys.tg", "n1p5." + i + "." + UUID.randomUUID(), "ENSEIGNANT", "PARENT");
        }
        entityManager.flush();
        entityManager.clear();
        stats.clear();
        lister(jeton, 0, 200);
        long avec5 = stats.getPrepareStatementCount();

        for (int i = 0; i < 20; i++) {
            creerCompteAffecte("n1p25." + i + "." + UUID.randomUUID() + "@edukeys.tg", "n1p25." + i + "." + UUID.randomUUID(), "ENSEIGNANT", "PARENT");
        }
        entityManager.flush();
        entityManager.clear();
        stats.clear();
        lister(jeton, 0, 200);
        long avec25 = stats.getPrepareStatementCount();

        assertThat(avec25).as("5 -> %d requêtes, 25 -> %d requêtes", avec5, avec25).isEqualTo(avec5);
    }

    @Test
    void empreinteCompteInconnu_seCorreleAvecCelleDeLaLimitationDeDebit_surLaValeurNormalisee() throws Exception {
        String valeur = " MAT-CORR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + " ";
        int statut = 401;
        for (int i = 0; i < 30 && statut != 429; i++) {
            statut = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifiant\":\"" + valeur + "\",\"motDePasse\":\"mauvais\"}"))
                    .andReturn().getResponse().getStatus();
        }
        assertThat(statut).as("la limitation par compte doit finir par répondre 429").isEqualTo(429);

        Pattern inconnu = Pattern.compile("motif=compte_inconnu empreinte=([0-9a-f]{16})");
        Pattern limite = Pattern.compile("motif=limitation_debit empreinteIdentifiant=([0-9a-f]{16})");
        String empreinteInconnu = null;
        String empreinteLimite = null;
        for (ILoggingEvent e : appender.list) {
            String message = e.getFormattedMessage();
            Matcher m1 = inconnu.matcher(message);
            if (m1.find()) {
                empreinteInconnu = m1.group(1);
            }
            Matcher m2 = limite.matcher(message);
            if (m2.find()) {
                empreinteLimite = m2.group(1);
            }
        }
        assertThat(empreinteInconnu).isNotNull();
        assertThat(empreinteLimite).isNotNull().isEqualTo(empreinteInconnu);
        assertThat(appender.list).noneMatch(e -> e.getFormattedMessage().toLowerCase().contains("mat-corr"))
                .noneMatch(e -> e.getFormattedMessage().contains("format_invalide"));
    }

    @Test
    void emission_reussitPourUnCompteDontTousLesRolesSontEleveEtParent() {
        String id = "mat-ep-" + UUID.randomUUID();
        creerCompteAffecte(null, id, "ELEVE", "PARENT");
        entityManager.flush();
        UUID cible = utilisateurRepository.findByIdentifiantConnexionAndActifTrue(id).orElseThrow().getId();

        try (var portee = ContexteEtablissement.ouvrir(UUID.fromString(ETABLISSEMENT_A))) {
            assertThat(emetteur.emettreAvecExpiration(cible, java.time.Instant.now().plus(java.time.Duration.ofDays(5)))).isNotBlank();
            entityManager.flush();
        }
    }
}
