package tg.novadigital.edukeys.eleve.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Blocs de détection des migrations V16 (doublons de dossiers) et V17 (codes d'établissement), rejoués
 * à l'identique depuis les fichiers de migration : ils doivent passer sur une base saine et échouer avec
 * un message actionnable — jamais une violation de contrainte brute — sur des données fautives.
 * Rollback transactionnel : les DDL de test (DROP INDEX / DROP CONSTRAINT) sont annulés avec lui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MigrationsUs08IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static String bloc(String migration, String debut, String fin) throws Exception {
        String sql = new ClassPathResource("db/migration/" + migration).getContentAsString(StandardCharsets.UTF_8);
        Matcher m = Pattern.compile(Pattern.quote(debut) + "(.*?)" + Pattern.quote(fin), Pattern.DOTALL).matcher(sql);
        assertThat(m.find()).as("bloc %s..%s dans %s", debut, fin, migration).isTrue();
        return m.group(1);
    }

    private String blocDoublons() throws Exception {
        return bloc("V16__eleve_inscriptions.sql", "-- DETECTION_DOUBLONS_DEBUT", "-- DETECTION_DOUBLONS_FIN");
    }

    private String blocCodes() throws Exception {
        String sql = new ClassPathResource("db/migration/V17__etablissements_code_ascii.sql").getContentAsString(StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(DO \\$\\$.*?\\$\\$;)", Pattern.DOTALL).matcher(sql);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    // ------------------------------------------------------------------
    // V16 : détection des doublons avant la recréation de l'index
    // ------------------------------------------------------------------

    @Test
    void v16_leBlocDeDetectionPasseSurUneBaseSaine() throws Exception {
        String bloc = blocDoublons();

        assertThatCode(() -> jdbcTemplate.execute(bloc)).doesNotThrowAnyException();
    }

    @Test
    void v16_leBlocNommeLesDossiersEnDoublon_auLieuDUneViolationDeContrainteBrute() throws Exception {
        ScenarioInscription scenario = new ScenarioInscription(mockMvc, jdbcTemplate, utilisateurRepository, passwordEncoder, entityManager);
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("MIG");
        ScenarioInscription.Dossier dossier = scenario.dossierEnAttente(etab, "Doublon", "Migration", "2015-05-05");
        entityManager.flush(); // le SQL direct qui suit doit voir le dossier créé par l'API

        // État que l'ancien index (V13) autorisait : un dossier EN_ATTENTE et un dossier ACCEPTEE du même enfant.
        jdbcTemplate.execute("drop index uk_demandes_admission_doublon");
        jdbcTemplate.update("create temp table copie_dossier on commit drop as select * from demandes_admission where id = ?::uuid", dossier.id());
        jdbcTemplate.update("update copie_dossier set id = ?, reference = 'PRE-DOUBLON-MIG', code_suivi = 'DOUBLONMIGRATION', statut = 'ACCEPTEE'",
                UUID.randomUUID());
        jdbcTemplate.execute("insert into demandes_admission select * from copie_dossier");
        String reference = jdbcTemplate.queryForObject("select reference from demandes_admission where id = ?::uuid", String.class, dossier.id());

        String bloc = blocDoublons();
        assertThatThrownBy(() -> jdbcTemplate.execute(bloc))
                .isNotInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Migration V16 impossible")
                .hasMessageContaining("1 groupe(s)")
                .hasMessageContaining(reference)
                .hasMessageContaining("PRE-DOUBLON-MIG")
                .hasMessageContaining("Marche a suivre");
    }

    // ------------------------------------------------------------------
    // V17 : format du code d'établissement
    // ------------------------------------------------------------------

    @Test
    void v17_leBlocDeDetectionPasseSurUneBaseSaine() throws Exception {
        String bloc = blocCodes();

        assertThatCode(() -> jdbcTemplate.execute(bloc)).doesNotThrowAnyException();
    }

    @Test
    void v17_leBlocNommeLesCodesFautifs_etLaContrainteRefuseUnCodeHorsFormat() throws Exception {
        jdbcTemplate.execute("alter table etablissements drop constraint ck_etablissements_code_format");
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, 'ÉCOLE-1', 'Ancien', 'COLLEGE', 'Lomé', ?, true, now(), now())",
                id, "ancien." + id + "@edukeys.tg");

        String bloc = blocCodes();
        jdbcTemplate.execute("savepoint avant_echec"); // une erreur SQL avorte la transaction : on la rattrape pour la suite du test
        assertThatThrownBy(() -> jdbcTemplate.execute(bloc))
                .hasMessageContaining("Migration V17 impossible")
                .hasMessageContaining("ÉCOLE-1");
        jdbcTemplate.execute("rollback to savepoint avant_echec");

        jdbcTemplate.update("update etablissements set code = 'OK1' where id = ?", id);
        assertThatCode(() -> jdbcTemplate.execute(bloc)).doesNotThrowAnyException();
    }

    @Test
    void v17_laContrainteDeBaseRefuseLesCodesHorsFormat() {
        for (String code : new String[] {"A", "ABCDEFGHIJK", "CS-J", "csj", "CS J", "ÉCOLE"}) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.execute("savepoint avant_insertion");
            assertThatThrownBy(() -> jdbcTemplate.update(
                            "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                                    + "values (?, ?, 'X', 'COLLEGE', 'Lomé', ?, true, now(), now())",
                            id, code, "x." + id + "@edukeys.tg"))
                    .as("code %s", code)
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbcTemplate.execute("rollback to savepoint avant_insertion");
        }
    }
}
