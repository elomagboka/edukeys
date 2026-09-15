package tg.novadigital.edukeys.academique.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration des années scolaires (US-01) : MockMvc + Testcontainers
 * PostgreSQL, rollback transactionnel (jamais de suppression). Couvre le
 * critère d'acceptation minimum (création, bascule d'activation), l'exercice
 * réel de {@code ex_annees_scolaires_chevauchement} en base (DELTA 2),
 * l'isolation multi-établissement et les permissions (règle 11 CLAUDE.md,
 * y compris le refus explicite de SUPER_ADMIN).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnneeScolaireControllerIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------
    // Critère d'acceptation : création avec dates de début/fin
    // ------------------------------------------------------------------

    @Test
    void creerAnneeScolaire_avecDatesDebutEtFin_retourne201() throws Exception {
        String etablissementId = creerEtablissement("CRE");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-01","dateFin":"2027-07-15"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("PREPARATION"))
                .andExpect(jsonPath("$.libelle").value("2026-2027"))
                .andExpect(jsonPath("$.actif").value(true));
    }

    /** DELTA 1 : libellé libre, aucun contrôle de format imposé. */
    @Test
    void creerAnneeScolaire_avecLibelleLibre_estRestitueInchange() throws Exception {
        String etablissementId = creerEtablissement("LIB");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-01","dateFin":"2027-07-15","libelle":"2026/2027"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("2026/2027"));
    }

    // ------------------------------------------------------------------
    // Critère d'acceptation : modification des dates
    // ------------------------------------------------------------------

    @Test
    void modifierAnneeScolaire_changeLesDatesEtConserveLeLibelleSansLibelleFourni() throws Exception {
        String etablissementId = creerEtablissement("MOD");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String reponseCreation = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15", "Rentrée 2026");
        String id = JsonPath.read(reponseCreation, "$.id");

        mockMvc.perform(put("/api/v1/annees-scolaires/" + id)
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-15","dateFin":"2027-07-20"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateDebut").value("2026-09-15"))
                .andExpect(jsonPath("$.dateFin").value("2027-07-20"))
                .andExpect(jsonPath("$.libelle").value("Rentrée 2026"));
    }

    // ------------------------------------------------------------------
    // Critères d'acceptation : bascule d'activation et clôture
    // ------------------------------------------------------------------

    @Test
    void activerAnneeScolaire_bascule_LancienneActivePasseCentClotureeEtUneSeuleResteActive() throws Exception {
        String etablissementId = creerEtablissement("ACT");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String reponsePremiere = creerAnneeScolaire(jetonAdmin, "2025-09-01", "2026-07-15", "2025-2026");
        String idPremiere = JsonPath.read(reponsePremiere, "$.id");
        mockMvc.perform(post("/api/v1/annees-scolaires/" + idPremiere + "/activation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));

        String reponseSeconde = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15", "2026-2027");
        String idSeconde = JsonPath.read(reponseSeconde, "$.id");

        mockMvc.perform(post("/api/v1/annees-scolaires/" + idSeconde + "/activation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));

        entityManager.flush();

        var lignesActives = jdbcTemplate.queryForList(
                "select id, statut from annees_scolaires where etablissement_id = ?::uuid and actif = true",
                etablissementId);
        assertThat(lignesActives).hasSize(2);
        assertThat(lignesActives.stream().filter(l -> "ACTIVE".equals(l.get("statut"))))
                .hasSize(1)
                .allSatisfy(ligne -> assertThat(ligne.get("id").toString()).isEqualTo(idSeconde));
        assertThat(lignesActives.stream().filter(l -> l.get("id").toString().equals(idPremiere))
                        .findFirst().orElseThrow().get("statut")).isEqualTo("CLOTUREE");

        mockMvc.perform(get("/api/v1/annees-scolaires/active")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idSeconde));
    }

    @Test
    void definitLanneeActive_puisConsultationRenvoieCetteAnneeActive() throws Exception {
        String etablissementId = creerEtablissement("DEF");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        String reponse = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15", "2026-2027");
        String id = JsonPath.read(reponse, "$.id");

        mockMvc.perform(get("/api/v1/annees-scolaires/active")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANNEE_SCOLAIRE_ACTIVE_ABSENTE"));

        mockMvc.perform(post("/api/v1/annees-scolaires/" + id + "/activation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/annees-scolaires/active")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    // ------------------------------------------------------------------
    // DELTA 2 : la contrainte d'exclusion est réellement exercée en base
    // ------------------------------------------------------------------

    /**
     * Preuve que {@code ex_annees_scolaires_chevauchement} est réellement
     * exercée EN BASE (DELTA 2), indépendamment de toute vérification
     * applicative : deux insertions SQL directes, la seconde chevauchant la
     * première, sans passer par {@code AnneeScolaireService} ni
     * {@code rechercherChevauchements}. Si l'index d'exclusion venait à
     * disparaître d'une migration future, ce test échouerait alors que la
     * couche service resterait verte.
     */
    @Test
    void contrainteDexclusionEnBase_refuseLeChevauchement_independammentDeLaVerificationApplicative() {
        UUID etablissementId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, 'COLLEGE', 'Lomé', ?, true, now(), now())",
                etablissementId, "XCL-" + etablissementId, "Établissement Exclusion Test", "xcl." + etablissementId + "@edukeys.tg");

        jdbcTemplate.update(
                "insert into annees_scolaires (id, etablissement_id, libelle, date_debut, date_fin, statut, actif, "
                        + "date_creation, date_modification) "
                        + "values (?, ?, ?, ?, ?, 'PREPARATION', true, now(), now())",
                UUID.randomUUID(), etablissementId, "DIRECT-2026", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 7, 15));

        // Savepoint manuel : sans lui, l'échec de l'exclusion (SQLState 25P02)
        // avorte toute la transaction du test, y compris le rollback final du
        // framework — la requête de vérification ci-dessous deviendrait
        // elle-même impossible ("current transaction is aborted").
        Object savepoint = jdbcTemplate.execute((java.sql.Connection connection) -> connection.setSavepoint());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into annees_scolaires (id, etablissement_id, libelle, date_debut, date_fin, statut, actif, "
                        + "date_creation, date_modification) "
                        + "values (?, ?, ?, ?, ?, 'PREPARATION', true, now(), now())",
                UUID.randomUUID(), etablissementId, "DIRECT-CHEVAUCHANTE", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 8, 1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_annees_scolaires_chevauchement");

        jdbcTemplate.execute((java.sql.Connection connection) -> {
            connection.rollback((java.sql.Savepoint) savepoint);
            return null;
        });

        var lignes = jdbcTemplate.queryForList(
                "select id from annees_scolaires where etablissement_id = ?", etablissementId);
        assertThat(lignes).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Isolation multi-établissement
    // ------------------------------------------------------------------

    @Test
    void refuse404_surUneAnneeDunAutreEtablissement_nonModifiableNiVisible() throws Exception {
        String etablissementA = creerEtablissement("ISOA");
        String etablissementB = creerEtablissement("ISOB");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String jetonAdminB = creerAdminEtObtenirToken(etablissementB);

        String reponse = creerAnneeScolaire(jetonAdminA, "2026-09-01", "2027-07-15", "ANNEE-A");
        String idAnneeA = JsonPath.read(reponse, "$.id");

        mockMvc.perform(get("/api/v1/annees-scolaires/" + idAnneeA)
                        .header("Authorization", "Bearer " + jetonAdminB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANNEE_SCOLAIRE_INTROUVABLE"));

        mockMvc.perform(put("/api/v1/annees-scolaires/" + idAnneeA)
                        .header("Authorization", "Bearer " + jetonAdminB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-15","dateFin":"2027-07-20"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANNEE_SCOLAIRE_INTROUVABLE"));
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    @Test
    void refuse403_unAppelantSansPermission() throws Exception {
        String etablissementId = creerEtablissement("PRM");
        String jetonSansPermission = creerUtilisateurSansRoleEtObtenirToken(etablissementId);

        mockMvc.perform(get("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonSansPermission))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonSansPermission)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-01","dateFin":"2027-07-15"}
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * Règle 11, CLAUDE.md : SUPER_ADMIN ne porte aucune permission métier
     * (ADR-0002) — piège déjà survenu sur ce projet (SiteController /
     * LogoController). Vérifié explicitement ici pour ANNEE_SCOLAIRE_*.
     */
    @Test
    void refuse403_unSuperAdmin_surLesEndpointsDAnneesScolaires() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-01","dateFin":"2027-07-15"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Assertions d'erreur sur $.code (jamais $.detail)
    // ------------------------------------------------------------------

    @Test
    void creerAnneeScolaire_avecDatesIncoherentes_retourne422AvecCodeMachine() throws Exception {
        String etablissementId = creerEtablissement("ERR");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);

        mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-01","dateFin":"2026-08-01"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ANNEE_SCOLAIRE_DATES_INCOHERENTES"));
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private String creerAnneeScolaire(String jeton, String dateDebut, String dateFin, String libelle) throws Exception {
        return mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"%s","dateFin":"%s","libelle":"%s"}
                                """.formatted(dateDebut, dateFin, libelle)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
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
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us01." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-01 Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        return connecterEtObtenirAccessToken(compteAdmin.getEmail());
    }

    /** Affectation active mais sans rôle : aucune permission, y compris ANNEE_SCOLAIRE_CONSULTER. */
    private String creerUtilisateurSansRoleEtObtenirToken(String etablissementId) throws Exception {
        Utilisateur compte = utilisateurRepository.save(new Utilisateur(
                "sanspermission.us01." + UUID.randomUUID() + "@edukeys.tg",
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
