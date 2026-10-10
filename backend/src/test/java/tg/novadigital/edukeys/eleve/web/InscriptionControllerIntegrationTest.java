package tg.novadigital.edukeys.eleve.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration US-08 : {@code POST /api/v1/inscriptions} (endpoint principal), règles d'affectation,
 * homonymes, permissions. Hors transaction de test : {@code InscriptionService.inscrire} refuse d'être appelé depuis une
 * transaction (ligne du compteur créée à part), donc le rollback transactionnel de test est inapplicable ici ;
 * chaque scénario crée son propre établissement (code unique) et rien n'est supprimé. Le rollback complet,
 * la concurrence et l'historisation Envers
 * (écrite au commit) sont couverts dans {@link InscriptionTransactionsReellesIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InscriptionControllerIntegrationTest {

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

    private ScenarioInscription scenario;

    @BeforeEach
    void preparer() {
        scenario = new ScenarioInscription(mockMvc, jdbcTemplate, utilisateurRepository, passwordEncoder, entityManager);
    }

    private ScenarioInscription.Etablissement etablissement(String prefixe) throws Exception {
        return scenario.etablissementPret(prefixe);
    }

    // ------------------------------------------------------------------
    // Endpoint principal
    // ------------------------------------------------------------------

    @Test
    void doitInscrireLEleve_creerSonCompte_etMarquerLeDossier() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INA");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", 30, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Kodjo", "Ama", "2015-05-12");
        Instant avant = Instant.now();

        String reponse = scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.matricule").value(etab.code() + "-2026-00001"))
                .andExpect(jsonPath("$.nom").value("Kodjo"))
                .andExpect(jsonPath("$.prenoms").value("Ama"))
                .andExpect(jsonPath("$.classe.id").value(classeId))
                .andExpect(jsonPath("$.classe.libelle").value("6ème A"))
                .andExpect(jsonPath("$.niveau.id").value(etab.niveauId()))
                .andExpect(jsonPath("$.filiere").doesNotExist())
                .andExpect(jsonPath("$.anneeScolaire.libelle").value("2026-2027"))
                .andExpect(jsonPath("$.siteId").value(scenario.idSitePrincipal(etab.id())))
                .andExpect(jsonPath("$.compte.identifiantConnexion").value((etab.code() + "-2026-00001").toLowerCase()))
                .andExpect(jsonPath("$.compte.motDePasseTemporaire").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Eleve, inscription et lien de dossier.
        String eleveId = JsonPath.read(reponse, "$.eleveId");
        Map<String, Object> eleve = jdbcTemplate.queryForMap(
                "select matricule, nom, prenoms, date_naissance::text as dn, lieu_naissance, sexe, nationalite, demande_admission_id::text as d, "
                        + "utilisateur_id::text as u from eleves where id = ?::uuid", eleveId);
        assertThat(eleve).containsEntry("matricule", etab.code() + "-2026-00001").containsEntry("nom", "Kodjo")
                .containsEntry("dn", "2015-05-12").containsEntry("lieu_naissance", "Lomé").containsEntry("sexe", "F")
                .containsEntry("d", dossier.id());
        Map<String, Object> inscription = jdbcTemplate.queryForMap(
                "select classe_id::text as c, site_id::text as s, actif from inscriptions where eleve_id = ?::uuid", eleveId);
        assertThat(inscription).containsEntry("c", classeId).containsEntry("s", scenario.idSitePrincipal(etab.id()));
        Map<String, Object> demande = jdbcTemplate.queryForMap(
                "select eleve_id::text as e, date_inscription from demandes_admission where id = ?::uuid", dossier.id());
        assertThat(demande.get("e")).isEqualTo(eleveId);
        assertThat(demande.get("date_inscription")).isNotNull();

        // Compte élève : actif, identifiant = matricule normalisé, sans email, rôle ELEVE seul, mot de passe à changer.
        Map<String, Object> compte = jdbcTemplate.queryForMap(
                "select identifiant_connexion, email, actif, mot_de_passe_a_changer from utilisateurs where id = ?::uuid", eleve.get("u"));
        assertThat(compte).containsEntry("identifiant_connexion", (etab.code() + "-2026-00001").toLowerCase())
                .containsEntry("actif", true).containsEntry("mot_de_passe_a_changer", true);
        assertThat(compte.get("email")).isNull();
        List<String> roles = jdbcTemplate.queryForList(
                "select r.role_code from affectation_roles r join affectations_etablissement a on a.id = r.affectation_id "
                        + "where a.utilisateur_id = ?::uuid and a.actif = true", String.class, eleve.get("u"));
        assertThat(roles).containsExactly("ELEVE");
        Long jetons = jdbcTemplate.queryForObject(
                "select count(*) from jetons_activation_compte where utilisateur_id = ?::uuid and actif = true", Long.class, eleve.get("u"));
        assertThat(jetons).isEqualTo(1L);

        // Expiration : au moins 14 jours après l'inscription.
        Instant expiration = Instant.parse(JsonPath.read(reponse, "$.compte.expiration"));
        assertThat(expiration).isAfter(avant.plusSeconds(14L * 24 * 3600 - 5));
        // Compteur : une ligne, dernier = 1.
        Long dernier = jdbcTemplate.queryForObject(
                "select dernier from compteurs_matricule where etablissement_id = ?::uuid and annee = 2026", Long.class, etab.id());
        assertThat(dernier).isEqualTo(1L);
    }

    @Test
    void doitRenvoyerLaFiliere_quandLaClasseEnPorteUne() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INP");
        String filiereId = scenario.creerFiliere(etab.jetonAdmin(), "Série scientifique", "SCI");
        String classeId = scenario.creerClasseAvecFiliere(etab.jetonAdmin(), etab.niveauId(), "S", filiereId);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Avec", "Filiere", "2015-08-08");

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filiere.id").value(filiereId))
                .andExpect(jsonPath("$.filiere.libelle").value("Série scientifique"));
    }

    @Test
    void lElevePeutSeConnecterAvecSonMatricule_enMajusculesComme_enMinuscules() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INB");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Yao", "Afi", "2016-02-02");

        String reponse = scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String matricule = JsonPath.read(reponse, "$.matricule");
        String motDePasse = JsonPath.read(reponse, "$.compte.motDePasseTemporaire");

        for (String identifiant : new String[] {matricule, matricule.toLowerCase()}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"identifiant":"%s","motDePasse":"%s"}
                                    """.formatted(identifiant, motDePasse)))
                    .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------
    // Dossier
    // ------------------------------------------------------------------

    @Test
    void doitRefuser_unDossierNonAccepte_en422() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INC");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierEnAttente(etab, "Ayele", "Koffi", "2015-03-03");

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSCRIPTION_DEMANDE_NON_ACCEPTEE"));
    }

    @Test
    void doitRefuser_unDossierDejaInscrit_en409_etUneVersionPerimee_en409() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("IND");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Sena", "Mawuli", "2015-04-04");

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version() + 5, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMISSION_MODIFICATION_CONCURRENTE"));

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false).andExpect(status().isCreated());
        ScenarioInscription.Dossier relu = scenario.relire(etab.jetonAdmin(), dossier.id());
        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, relu.version(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSCRIPTION_DEJA_EFFECTUEE"));
    }

    @Test
    void doitRefuser_undossierInconnu_en404() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INE");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);

        scenario.inscrire(etab.jetonAdmin(), UUID.randomUUID().toString(), classeId, 0, false)
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Affectation à la classe
    // ------------------------------------------------------------------

    @Test
    void doitRefuser_unNiveauDeClasseDifferent_en422() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INF");
        String autreNiveau = scenario.creerNiveau(etab.jetonAdmin(), "5ème", "INF5", 2, etab.cycleId());
        String classeAutreNiveau = scenario.creerClasse(etab.jetonAdmin(), autreNiveau, "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Edem", "Fafa", "2015-06-06");

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeAutreNiveau, dossier.version(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSCRIPTION_NIVEAU_INCOHERENT"));
    }

    @Test
    void doitRefuser_uneClasseInactive_en422() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("ING");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Adzo", "Mensah", "2015-07-07");
        jdbcTemplate.update("update classes set actif = false where id = ?::uuid", classeId);

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSCRIPTION_CLASSE_INACTIVE"));
    }

    @Test
    void doitRefuser_uneClasseDUnAutreEtablissement_en404() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INH");
        ScenarioInscription.Etablissement autre = etablissement("INI");
        String classeAutre = scenario.creerClasse(autre.jetonAdmin(), autre.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Kossi", "Abra", "2015-08-08");

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeAutre, dossier.version(), false)
                .andExpect(status().isNotFound());
    }

    @Test
    void doitRefuser_uneAnneeCloturee_en422() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INJ");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Mawuena", "Kossi", "2015-09-09");
        // Activer l'année suivante clôture l'année 2026-2027 de la classe et du dossier.
        scenario.creerAnnee(etab.jetonAdmin(), "2027-09-01", "2028-07-15", true);

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSCRIPTION_ANNEE_CLOTUREE"));
    }

    @Test
    void doitRefuser_uneClasseComplete_en422_etAccepterUneClasseSansLimite() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INK");
        String classeUneplace = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", 1, null);
        String classeSansLimite = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "B", null, null);
        ScenarioInscription.Dossier premier = scenario.dossierAccepte(etab, "Premier", "Eleve", "2015-01-01");
        ScenarioInscription.Dossier second = scenario.dossierAccepte(etab, "Second", "Eleve", "2015-02-02");
        ScenarioInscription.Dossier troisieme = scenario.dossierAccepte(etab, "Troisieme", "Eleve", "2015-03-03");

        scenario.inscrire(etab.jetonAdmin(), premier.id(), classeUneplace, premier.version(), false).andExpect(status().isCreated());
        scenario.inscrire(etab.jetonAdmin(), second.id(), classeUneplace, second.version(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSCRIPTION_CLASSE_COMPLETE"));
        scenario.inscrire(etab.jetonAdmin(), second.id(), classeSansLimite, second.version(), false).andExpect(status().isCreated());
        scenario.inscrire(etab.jetonAdmin(), troisieme.id(), classeSansLimite, troisieme.version(), false).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Homonymes
    // ------------------------------------------------------------------

    @Test
    void doitSignalerUnHomonyme_en409AvecSesDetails_puisInscrireApresConfirmation() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INL");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier premier = scenario.dossierAccepte(etab, "Agbeko", "Selom", "2015-05-05");
        scenario.inscrire(etab.jetonAdmin(), premier.id(), classeId, premier.version(), false).andExpect(status().isCreated());
        // Même enfant sous une graphie accentuée/casse différente : un 2e dossier est possible une fois le 1er inscrit ?
        // Non (Q5 : l'enfant ACCEPTEE reste « vivant ») — l'homonyme est donc un dossier sur une AUTRE année ou un autre
        // établissement de dossier ; on l'obtient ici en désactivant logiquement le premier dossier.
        jdbcTemplate.update("update demandes_admission set actif = false where id = ?::uuid", premier.id());
        ScenarioInscription.Dossier homonyme = scenario.dossierAccepte(etab, "AGBÉKO", "selom", "2015-05-05");

        scenario.inscrire(etab.jetonAdmin(), homonyme.id(), classeId, homonyme.version(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ELEVE_HOMONYME"))
                .andExpect(jsonPath("$.details.homonymes[0].matricule").value(etab.code() + "-2026-00001"))
                .andExpect(jsonPath("$.details.homonymes[0].classe").value("6ème A"));

        scenario.inscrire(etab.jetonAdmin(), homonyme.id(), classeId, homonyme.version(), true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value(etab.code() + "-2026-00002"));
    }

    // ------------------------------------------------------------------
    // Sécurité et validation
    // ------------------------------------------------------------------

    @Test
    void seulAdminPeutInscrire_direction_gestionnaire_superAdmin_et_anonyme_sontRefuses() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INM");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Securite", "Test", "2015-10-10");

        for (String role : new String[] {"DIRECTION", "GESTIONNAIRE", "ENSEIGNANT"}) {
            String jeton = scenario.jetonPourRole(etab.id(), role);
            scenario.inscrire(jeton, dossier.id(), classeId, dossier.version(), false).andExpect(status().isForbidden());
        }
        String jetonSuperAdmin = scenario.connecter(ScenarioInscription.EMAIL_SUPER_ADMIN);
        scenario.inscrire(jetonSuperAdmin, dossier.id(), classeId, dossier.version(), false).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content(ScenarioInscription.corps(dossier.id(), classeId, dossier.version(), false)))
                .andExpect(status().isUnauthorized());

        Long eleves = jdbcTemplate.queryForObject("select count(*) from eleves where etablissement_id = ?::uuid", Long.class, etab.id());
        assertThat(eleves).isZero();
    }

    @Test
    void doitRefuser403_unAdminDontLeMotDePasseDoitEtreChange_etUnCompteEleve() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INO");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Garde", "Mdp", "2015-11-11");
        ScenarioInscription.Dossier autre = scenario.dossierAccepte(etab, "Garde", "Eleve", "2015-12-12");

        // Un ADMIN dont le mot de passe est encore temporaire est bloqué par la garde centrale.
        scenario.jetonPourRole(etab.id(), "ADMIN");
        String emailAdmin = jdbcTemplate.queryForObject(
                "select u.email from utilisateurs u join affectations_etablissement a on a.utilisateur_id = u.id "
                        + "where a.etablissement_id = ?::uuid and u.email like 'u.jetable.%' limit 1", String.class, etab.id());
        jdbcTemplate.update("update utilisateurs set mot_de_passe_a_changer = true where email = ?", emailAdmin);
        String jetonTemporaire = scenario.connecter(emailAdmin);
        scenario.inscrire(jetonTemporaire, dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));

        // Un élève fraîchement inscrit (rôle ELEVE seul, mot de passe à changer) ne peut pas inscrire à son tour.
        String reponse = scenario.inscrire(etab.jetonAdmin(), autre.id(), classeId, autre.version(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String jetonEleve = scenario.connecter(JsonPath.read(reponse, "$.compte.identifiantConnexion"),
                JsonPath.read(reponse, "$.compte.motDePasseTemporaire"));
        scenario.inscrire(jetonEleve, dossier.id(), classeId, dossier.version(), false).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("select count(*) from eleves where etablissement_id = ?::uuid", Long.class, etab.id()))
                .isEqualTo(1L);
    }

    @Test
    void doitRejeter400_quandLaRequeteEstIncomplete() throws Exception {
        ScenarioInscription.Etablissement etab = etablissement("INN");

        mockMvc.perform(post("/api/v1/inscriptions")
                        .header("Authorization", "Bearer " + etab.jetonAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmerHomonyme\":false}"))
                .andExpect(status().isBadRequest());
    }
}
