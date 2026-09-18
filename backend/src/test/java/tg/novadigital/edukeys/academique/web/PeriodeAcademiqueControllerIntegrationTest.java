package tg.novadigital.edukeys.academique.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration des périodes académiques (US-05) : MockMvc +
 * Testcontainers PostgreSQL, rollback transactionnel. Couvre la création, le
 * refus 422 hors bornes d'année, le conflit 409 (contrainte d'exclusion
 * réellement exercée en base) et l'isolation multi-établissement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PeriodeAcademiqueControllerIntegrationTest {

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
    // Critère d'acceptation : création d'une période
    // ------------------------------------------------------------------

    @Test
    void creerPeriodeAcademique_avecDatesValides_retourne201() throws Exception {
        String etablissementId = creerEtablissement("CRE");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-11-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("1er trimestre"))
                .andExpect(jsonPath("$.type").value("TRIMESTRE"))
                .andExpect(jsonPath("$.anneeScolaireId").value(idAnnee))
                .andExpect(jsonPath("$.actif").value(true));
    }

    // ------------------------------------------------------------------
    // R3 : dates hors bornes de l'année -> 422
    // ------------------------------------------------------------------

    @Test
    void creerPeriodeAcademique_horsBornesDeLannee_retourne422() throws Exception {
        String etablissementId = creerEtablissement("HRS");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"Avant l'année","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-08-01","dateFin":"2026-08-25"}
                                """.formatted(idAnnee)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIODE_ACADEMIQUE_HORS_BORNES_ANNEE"));
    }

    // ------------------------------------------------------------------
    // R4 : la contrainte d'exclusion est réellement exercée en base
    // ------------------------------------------------------------------

    @Test
    void creerPeriodeAcademique_chevauchantUneAutrePeriodeActive_retourne409() throws Exception {
        String etablissementId = creerEtablissement("CHV");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-11-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"2e trimestre","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2026-11-01","dateFin":"2027-01-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIODE_ACADEMIQUE_PERIODE_CHEVAUCHANTE"));
    }

    @Test
    void creerPeriodeAcademique_libelleDejaUtilise_retourne409() throws Exception {
        String etablissementId = creerEtablissement("LIB");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-11-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2027-01-01","dateFin":"2027-03-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIODE_ACADEMIQUE_LIBELLE_DUPLIQUE"));
    }

    // ------------------------------------------------------------------
    // R4 : les cinq cas de chevauchement, exercés en base (contrainte GiST)
    // ------------------------------------------------------------------

    @Test
    void creerPeriodeAcademique_englobantUneAutrePeriodeActive_retourne409() throws Exception {
        String etablissementId = creerEtablissement("ENG");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-10-01","dateFin":"2026-11-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"Semestre englobant","type":"SEMESTRE","ordre":2,
                                 "dateDebut":"2026-09-01","dateFin":"2026-12-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIODE_ACADEMIQUE_PERIODE_CHEVAUCHANTE"));
    }

    @Test
    void creerPeriodeAcademique_engobeeDansUneAutrePeriodeActive_retourne409() throws Exception {
        String etablissementId = creerEtablissement("EMB");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"Semestre 1","type":"SEMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2027-01-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"Trimestre englobé","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2026-10-01","dateFin":"2026-11-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIODE_ACADEMIQUE_PERIODE_CHEVAUCHANTE"));
    }

    @Test
    void creerPeriodeAcademique_avantSansChevauchement_retourne201() throws Exception {
        String etablissementId = creerEtablissement("AVT");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"2e trimestre","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2026-12-01","dateFin":"2027-01-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-11-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());
    }

    @Test
    void creerPeriodeAcademique_apresSansChevauchement_retourne201() throws Exception {
        String etablissementId = creerEtablissement("APR");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-11-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"2e trimestre","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2026-12-01","dateFin":"2027-01-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());
    }

    @Test
    void creerPeriodeAcademique_jointiveSansChevauchement_retourne201() throws Exception {
        String etablissementId = creerEtablissement("JNT");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-10-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        // Contiguë, jointive : T2 commence le lendemain de la fin de T1, sans se chevaucher (bornes '[]').
        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"2e trimestre","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2026-10-02","dateFin":"2026-12-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());
    }

    @Test
    void creerPeriodeAcademique_demarrantLeJourDeFinDeLaPrecedente_retourne409() throws Exception {
        String etablissementId = creerEtablissement("BRN");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-12-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        // Borne exacte de la contrainte d'exclusion : les bornes '[]' sont fermées des deux côtés,
        // donc le 15/12 appartient aux deux intervalles et le chevauchement est d'un jour.
        // Vérifié par insertion SQL directe : Postgres normalise en [2026-09-01,2026-12-16).
        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"2e trimestre","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2026-12-15","dateFin":"2027-03-20"}
                                """.formatted(idAnnee)))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Trou entre périodes autorisé (non-régression : pas de couverture continue exigée)
    // ------------------------------------------------------------------

    @Test
    void creerPeriodeAcademique_avecTrouEntreDeuxPeriodes_retourne201() throws Exception {
        String etablissementId = creerEtablissement("TRU");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-10-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        // Vacances de Noël entre les deux périodes : aucune continuité exigée (R4 = non-chevauchement seul).
        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"2e trimestre","type":"TRIMESTRE","ordre":2,
                                 "dateDebut":"2026-11-01","dateFin":"2026-12-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // R6 : aucune contrainte d'homogénéité de type (non-régression)
    // ------------------------------------------------------------------

    @Test
    void creerPeriodeAcademique_trimestreEtSemestreSurLaMemeAnnee_coexistent() throws Exception {
        String etablissementId = creerEtablissement("MIX");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-10-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"Semestre 2","type":"SEMESTRE","ordre":3,
                                 "dateDebut":"2027-01-01","dateFin":"2027-06-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SEMESTRE"));
    }

    // ------------------------------------------------------------------
    // R10 : désactivation logique seule
    // ------------------------------------------------------------------

    @Test
    void desactiverPeriodeAcademique_libereLibelleEtOrdrePourUneNouvellePeriode() throws Exception {
        String etablissementId = creerEtablissement("DES");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        String reponse = mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-10-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idPeriode = JsonPath.read(reponse, "$.id");

        mockMvc.perform(post("/api/v1/periodes-academiques/" + idPeriode + "/desactivation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNoContent());

        // Le même libellé et le même ordre redeviennent disponibles : preuve d'une désactivation logique,
        // pas d'une suppression physique masquant un nouvel enregistrement identique.
        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-10-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // R7 : période « en cours » — dates construites autour d'aujourd'hui,
    // jamais une date fixée en dur, pour ne pas dépendre du jour d'exécution.
    // ------------------------------------------------------------------

    @Test
    void obtenirEnCours_retourneLaPeriodeContenantAujourdHui() throws Exception {
        String etablissementId = creerEtablissement("ENC");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        java.time.LocalDate debutAnnee = java.time.LocalDate.now().minusDays(60);
        java.time.LocalDate finAnnee = java.time.LocalDate.now().plusDays(200);
        String idAnnee = creerAnneeScolaire(jetonAdmin, debutAnnee.toString(), finAnnee.toString());

        mockMvc.perform(post("/api/v1/annees-scolaires/" + idAnnee + "/activation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());

        java.time.LocalDate debutPeriode = java.time.LocalDate.now().minusDays(10);
        java.time.LocalDate finPeriode = java.time.LocalDate.now().plusDays(30);
        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"Période en cours","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"%s","dateFin":"%s"}
                                """.formatted(idAnnee, debutPeriode, finPeriode)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/periodes-academiques/en-cours")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Période en cours"))
                .andExpect(jsonPath("$.enCours").value(true));
    }

    @Test
    void obtenirEnCours_retourne404_quandAucunePeriodeNeContientAujourdHui() throws Exception {
        String etablissementId = creerEtablissement("NEC");
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        java.time.LocalDate debutAnnee = java.time.LocalDate.now().minusDays(60);
        java.time.LocalDate finAnnee = java.time.LocalDate.now().plusDays(200);
        String idAnnee = creerAnneeScolaire(jetonAdmin, debutAnnee.toString(), finAnnee.toString());

        mockMvc.perform(post("/api/v1/annees-scolaires/" + idAnnee + "/activation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());

        // Période entièrement passée : aujourd'hui n'est dans aucune plage.
        java.time.LocalDate debutPeriode = java.time.LocalDate.now().minusDays(59);
        java.time.LocalDate finPeriode = java.time.LocalDate.now().minusDays(30);
        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"Période passée","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"%s","dateFin":"%s"}
                                """.formatted(idAnnee, debutPeriode, finPeriode)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/periodes-academiques/en-cours")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERIODE_ACADEMIQUE_EN_COURS_ABSENTE"));
    }

    // ------------------------------------------------------------------
    // Isolation multi-établissement
    // ------------------------------------------------------------------

    @Test
    void refuse404_surUnePeriodeDunAutreEtablissement() throws Exception {
        String etablissementA = creerEtablissement("ISOA");
        String etablissementB = creerEtablissement("ISOB");
        String jetonAdminA = creerAdminEtObtenirToken(etablissementA);
        String jetonAdminB = creerAdminEtObtenirToken(etablissementB);
        String idAnnee = creerAnneeScolaire(jetonAdminA, "2026-09-01", "2027-07-15");

        String reponse = mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-11-15"}
                                """.formatted(idAnnee)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idPeriode = JsonPath.read(reponse, "$.id");

        mockMvc.perform(get("/api/v1/periodes-academiques/" + idPeriode)
                        .header("Authorization", "Bearer " + jetonAdminB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERIODE_ACADEMIQUE_INTROUVABLE"));
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    @Test
    void refuse403_unAppelantSansPermission() throws Exception {
        String etablissementId = creerEtablissement("PRM");
        String jetonSansPermission = creerUtilisateurSansRoleEtObtenirToken(etablissementId);

        mockMvc.perform(get("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonSansPermission))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse403_unSuperAdmin_surLesEndpointsDePeriodesAcademiques() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse403_unEnseignant_surLaCreationDunePeriode() throws Exception {
        String etablissementId = creerEtablissement("ENS");
        String jetonEnseignant = creerEnseignantEtObtenirToken(etablissementId);
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        String idAnnee = creerAnneeScolaire(jetonAdmin, "2026-09-01", "2027-07-15");

        // PERIODE_CONSULTER (lecture) est accordée à ENSEIGNANT, mais pas PERIODE_GERER (écriture).
        mockMvc.perform(get("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonEnseignant))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/periodes-academiques")
                        .header("Authorization", "Bearer " + jetonEnseignant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anneeScolaireId":"%s","libelle":"1er trimestre","type":"TRIMESTRE","ordre":1,
                                 "dateDebut":"2026-09-01","dateFin":"2026-10-01"}
                                """.formatted(idAnnee)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private String creerAnneeScolaire(String jeton, String dateDebut, String dateFin) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"%s","dateFin":"%s"}
                                """.formatted(dateDebut, dateFin)))
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
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us05." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-05 Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        return connecterEtObtenirAccessToken(compteAdmin.getEmail());
    }

    private String creerEnseignantEtObtenirToken(String etablissementId) throws Exception {
        Utilisateur compte = utilisateurRepository.save(new Utilisateur(
                "enseignant.us05." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Enseignant US-05 Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ENSEIGNANT')", affectationId);

        return connecterEtObtenirAccessToken(compte.getEmail());
    }

    private String creerUtilisateurSansRoleEtObtenirToken(String etablissementId) throws Exception {
        Utilisateur compte = utilisateurRepository.save(new Utilisateur(
                "sanspermission.us05." + UUID.randomUUID() + "@edukeys.tg",
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
