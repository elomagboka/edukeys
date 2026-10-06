package tg.novadigital.edukeys.eleve.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import tg.novadigital.edukeys.academique.ClasseInscriptionQuery;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Inscription en VRAIES transactions (US-08) : pas de {@code @Transactional} de classe, chaque scénario
 * committe pour de bon avec son propre établissement (jamais nettoyé par suppression, CLAUDE.md règle 4).
 * Seul moyen de prouver la concurrence (verrous, compteur, ligne de compteur absente), le rollback complet
 * d'un échec tardif et l'historisation Envers, écrite au commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InscriptionTransactionsReellesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ClasseInscriptionQuery classeInscriptionQuery;

    private ScenarioInscription scenario;
    private ExecutorService executeur;

    @BeforeEach
    void preparer() {
        scenario = new ScenarioInscription(mockMvc, jdbcTemplate, utilisateurRepository, passwordEncoder, entityManager);
        executeur = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void arreter() {
        executeur.shutdownNow();
    }

    private record Reponse(int statut, String corps) {
    }

    /** Lance les appels « en même temps » (barrière de départ) et rend leurs réponses dans l'ordre. */
    private List<Reponse> enParallele(List<Callable<Reponse>> appels) throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Reponse>> futurs = new ArrayList<>();
        for (Callable<Reponse> appel : appels) {
            futurs.add(executeur.submit(() -> {
                depart.await();
                return appel.call();
            }));
        }
        depart.countDown();
        List<Reponse> reponses = new ArrayList<>();
        for (Future<Reponse> futur : futurs) {
            reponses.add(futur.get(30, TimeUnit.SECONDS));
        }
        return reponses;
    }

    private Callable<Reponse> inscription(ScenarioInscription.Etablissement etab, ScenarioInscription.Dossier dossier, String classeId) {
        return () -> {
            var resultat = scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false).andReturn();
            return new Reponse(resultat.getResponse().getStatus(), resultat.getResponse().getContentAsString());
        };
    }

    private Long compter(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Long.class, arguments);
    }

    // ------------------------------------------------------------------
    // Matricule : séquence unique sous concurrence, ligne du compteur absente
    // ------------------------------------------------------------------

    @Test
    void deuxPremieresInscriptionsDeLAnneeEnParallele_sansLigneDeCompteur_donnentDeuxFoisCreated_etDeuxSequencesConsecutives() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRA");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier d1 = scenario.dossierAccepte(etab, "Premier", "Parallele", "2015-01-01");
        ScenarioInscription.Dossier d2 = scenario.dossierAccepte(etab, "Second", "Parallele", "2015-02-02");
        // Motif exact de CompteurReferenceAdmission : la ligne du compteur n'est PAS pré-insérée.
        assertThat(compter("select count(*) from compteurs_matricule where etablissement_id = ?::uuid", etab.id())).isZero();

        List<Reponse> reponses = enParallele(List.of(inscription(etab, d1, classeId), inscription(etab, d2, classeId)));

        assertThat(reponses).extracting(Reponse::statut).containsExactly(201, 201);
        List<String> matricules = reponses.stream().map(r -> (String) JsonPath.read(r.corps(), "$.matricule")).sorted().toList();
        assertThat(matricules).containsExactly(etab.code() + "-2026-00001", etab.code() + "-2026-00002");
        assertThat(compter("select count(*) from compteurs_matricule where etablissement_id = ?::uuid", etab.id())).isEqualTo(1L);
        assertThat(compter("select dernier from compteurs_matricule where etablissement_id = ?::uuid", etab.id())).isEqualTo(2L);
    }

    /**
     * Les tests ci-dessus inscrivent dans UNE classe : le verrou de classe sérialise alors tout, et masquerait
     * l'absence du verrou du compteur (mutation constatée : retirer le verrou du compteur les laissait verts).
     * Ici chaque inscription vise une classe différente : seul le verrou du compteur les départage.
     */
    @Test
    void quatreInscriptionsParallelesDansQuatreClassesDistinctes_donnentQuatreSequencesDistinctesSansTrou() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRI");
        List<Callable<Reponse>> appels = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "C" + i, null, null);
            ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Sequence" + i, "Parallele", "2015-0" + (i + 1) + "-15");
            appels.add(inscription(etab, dossier, classeId));
        }

        List<Reponse> reponses = enParallele(appels);

        assertThat(reponses).extracting(Reponse::statut).containsOnly(201);
        List<String> matricules = reponses.stream().map(r -> (String) JsonPath.read(r.corps(), "$.matricule")).sorted().toList();
        assertThat(matricules).containsExactly(etab.code() + "-2026-00001", etab.code() + "-2026-00002",
                etab.code() + "-2026-00003", etab.code() + "-2026-00004");
        assertThat(compter("select dernier from compteurs_matricule where etablissement_id = ?::uuid", etab.id())).isEqualTo(4L);
    }

    @Test
    void doubleClicSurLeMemeDossier_donneUnCreated_etUnConflit_etUnSeulEleve() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRB");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Double", "Clic", "2015-03-03");

        List<Reponse> reponses = enParallele(List.of(inscription(etab, dossier, classeId), inscription(etab, dossier, classeId)));

        assertThat(reponses).extracting(Reponse::statut).containsExactlyInAnyOrder(201, 409);
        assertThat(compter("select count(*) from eleves where etablissement_id = ?::uuid", etab.id())).isEqualTo(1L);
        assertThat(compter("select count(*) from inscriptions where etablissement_id = ?::uuid", etab.id())).isEqualTo(1L);
        assertThat(compter("select dernier from compteurs_matricule where etablissement_id = ?::uuid", etab.id())).isEqualTo(1L);
    }

    @Test
    void derniereplaceDisputee_donneUnCreated_etUn422() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRC");
        String classeUneplace = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", 1, null);
        ScenarioInscription.Dossier d1 = scenario.dossierAccepte(etab, "Course", "Un", "2015-04-04");
        ScenarioInscription.Dossier d2 = scenario.dossierAccepte(etab, "Course", "Deux", "2015-05-05");

        List<Reponse> reponses = enParallele(List.of(inscription(etab, d1, classeUneplace), inscription(etab, d2, classeUneplace)));

        assertThat(reponses).extracting(Reponse::statut).containsExactlyInAnyOrder(201, 422);
        assertThat(compter("select count(*) from inscriptions where classe_id = ?::uuid", classeUneplace)).isEqualTo(1L);
        // Le refus pour classe pleine intervient AVANT le matricule : un seul numéro consommé.
        assertThat(compter("select dernier from compteurs_matricule where etablissement_id = ?::uuid", etab.id())).isEqualTo(1L);
    }

    @Test
    void echecApresLIncrement_identifiantDeCompteDejaPris_annuleTout_etNeConsommeAucuneSequence() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRD");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Identifiant", "Pris", "2015-06-06");
        String matriculeAttendu = etab.code() + "-2026-00001";
        UUID parasite = UUID.randomUUID();
        // Compte sans email (comme un élève) et rattaché à l'établissement en ELEVE : exclu de la liste de plateforme,
        // dont d'autres tests vérifient le tri par email et la cohérence identifiant/email de tous les comptes listés.
        jdbcTemplate.update(
                "insert into utilisateurs (id, identifiant_connexion, mot_de_passe_hache, nom_complet, super_admin, mot_de_passe_a_changer, actif, "
                        + "date_creation, date_modification) values (?, ?, 'x', 'Parasite', false, false, true, now(), now())",
                parasite, matriculeAttendu.toLowerCase());
        UUID affectationParasite = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())", affectationParasite, parasite, etab.id());
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ELEVE')", affectationParasite);

        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UTILISATEUR_IDENTIFIANT_DUPLIQUE"));

        // Rollback complet : ni élève, ni inscription, ni lien de dossier, séquence NON consommée.
        assertThat(compter("select count(*) from eleves where etablissement_id = ?::uuid", etab.id())).isZero();
        assertThat(compter("select count(*) from inscriptions where etablissement_id = ?::uuid", etab.id())).isZero();
        assertThat(compter("select count(*) from demandes_admission where id = ?::uuid and eleve_id is not null", dossier.id())).isZero();
        assertThat(compter("select dernier from compteurs_matricule where etablissement_id = ?::uuid and annee = 2026", etab.id())).isZero();

        // Le numéro 00001 reste disponible : libérer l'identifiant, puis réessayer.
        jdbcTemplate.update("update utilisateurs set actif = false, date_desactivation = now() where id = ?", parasite);
        scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value(matriculeAttendu));
    }

    @Test
    void expirationHorsBornes_donne422_etAnnuleTout() throws Exception {
        // Rentrée dans plus de 240 jours : début d'année + 30 j dépasse le maximum du port, jamais ramené en silence.
        String etablissementId = scenario.creerEtablissement("TRE");
        String code = jdbcTemplate.queryForObject("select code from etablissements where id = ?::uuid", String.class, etablissementId);
        String jeton = scenario.jetonPourRole(etablissementId, "ADMIN");
        String anneeId = scenario.creerAnnee(jeton, "2029-09-01", "2030-07-15", false);
        String cycleId = scenario.creerCycle(jeton, "Collège", "TREC", 1);
        String niveauId = scenario.creerNiveau(jeton, "6ème", "TREN", 1, cycleId);
        jdbcTemplate.update("update etablissements set admissions_ouvertes = true where id = ?::uuid", etablissementId);
        ScenarioInscription.Etablissement etab = new ScenarioInscription.Etablissement(etablissementId, code, jeton, cycleId, niveauId);
        String classeId = scenario.creerClasse(jeton, niveauId, "A", null, anneeId);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Trop", "Tot", "2015-07-07");

        scenario.inscrire(jeton, dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_TEMPORAIRE_EXPIRATION_HORS_BORNES"));

        assertThat(compter("select count(*) from eleves where etablissement_id = ?::uuid", etablissementId)).isZero();
        assertThat(compter("select count(*) from inscriptions where etablissement_id = ?::uuid", etablissementId)).isZero();
        assertThat(compter("select count(*) from utilisateurs where identifiant_connexion = ?", (code + "-2029-00001").toLowerCase())).isZero();
        assertThat(compter("select dernier from compteurs_matricule where etablissement_id = ?::uuid and annee = 2029", etablissementId)).isZero();
        assertThat(compter("select count(*) from demandes_admission where id = ?::uuid and eleve_id is not null", dossier.id())).isZero();
    }

    // ------------------------------------------------------------------
    // Matricule jamais réattribué, compteur jamais désactivé
    // ------------------------------------------------------------------

    @Test
    void leMatriculeDUnEleveDesactiveNEstJamaisReattribue_etLeCompteurNeSeDesactivePas() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRF");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier d1 = scenario.dossierAccepte(etab, "Radie", "Un", "2015-08-08");
        ScenarioInscription.Dossier d2 = scenario.dossierAccepte(etab, "Radie", "Deux", "2015-09-09");

        String reponse = scenario.inscrire(etab.jetonAdmin(), d1.id(), classeId, d1.version(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        jdbcTemplate.update("update eleves set actif = false, date_desactivation = now() where id = ?::uuid", (String) JsonPath.read(reponse, "$.eleveId"));

        scenario.inscrire(etab.jetonAdmin(), d2.id(), classeId, d2.version(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value(etab.code() + "-2026-00002"));

        // Base : un compteur ne se désactive pas (CHECK) ; unicité absolue du matricule (même désactivé).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                        "update compteurs_matricule set actif = false where etablissement_id = ?::uuid", etab.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                        "update eleves set matricule = (select matricule from eleves where id = ?::uuid) where id = ?::uuid",
                        JsonPath.read(reponse, "$.eleveId"), jdbcTemplate.queryForObject(
                                "select id::text from eleves where etablissement_id = ?::uuid and actif = true", String.class, etab.id())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // Verrou de classe : deux classes d'un même niveau ne se bloquent pas
    // ------------------------------------------------------------------

    @Test
    void deuxClassesDUnMemeNiveau_nePartagentPasLeVerrou_maisUneMemeClasseSeSerialise() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRG");
        String classeA = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        String classeB = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "B", null, null);
        ScenarioInscription.Dossier dossierA = scenario.dossierAccepte(etab, "Verrou", "A", "2015-10-10");
        ScenarioInscription.Dossier dossierB = scenario.dossierAccepte(etab, "Verrou", "B", "2015-11-11");

        CountDownLatch verrouPris = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        // Un tiers tient le verrou de la classe A (comme une inscription longue ou le futur US-11).
        Future<?> detenteur = executeur.submit(() -> {
            try (var portee = ContexteEtablissement.ouvrir(UUID.fromString(etab.id()))) {
                new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    classeInscriptionQuery.verrouillerPourInscription(UUID.fromString(classeA));
                    verrouPris.countDown();
                    try {
                        liberer.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        });
        assertThat(verrouPris.await(15, TimeUnit.SECONDS)).isTrue();
        try {
            // Classe B : aucune attente, malgré le verrou tenu sur A (même niveau, même année, même site).
            Future<Reponse> surB = executeur.submit(inscription(etab, dossierB, classeB));
            assertThat(surB.get(15, TimeUnit.SECONDS).statut()).isEqualTo(201);

            // Classe A : l'inscription attend la libération du verrou.
            Future<Reponse> surA = executeur.submit(inscription(etab, dossierA, classeA));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> surA.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();
            assertThat(surA.get(15, TimeUnit.SECONDS).statut()).isEqualTo(201);
        } finally {
            liberer.countDown();
        }
        try {
            detenteur.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError("le détenteur du verrou a échoué", e.getCause());
        }
    }

    // ------------------------------------------------------------------
    // Envers
    // ------------------------------------------------------------------

    @Test
    void lInscriptionEstHistorisee_eleve_inscription_compteur_etDossier() throws Exception {
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("TRH");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Histoire", "Eleve", "2015-12-12");

        String reponse = scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String eleveId = JsonPath.read(reponse, "$.eleveId");
        String inscriptionId = JsonPath.read(reponse, "$.inscriptionId");
        assertThat(compter("select count(*) from eleves_aud where id = ?::uuid and revtype = 0", eleveId)).isEqualTo(1L);
        assertThat(compter("select count(*) from inscriptions_aud where id = ?::uuid and revtype = 0", inscriptionId)).isEqualTo(1L);
        assertThat(compter("select count(*) from compteurs_matricule_aud where etablissement_id = ?::uuid", etab.id())).isGreaterThanOrEqualTo(1L);
        assertThat(compter("select count(*) from demandes_admission_aud where id = ?::uuid and eleve_id = ?::uuid", dossier.id(), eleveId))
                .isEqualTo(1L);
    }
}
