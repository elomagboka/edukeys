package tg.novadigital.edukeys.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * 3e revue, point 2 : {@link GenerateurReferenceAdmission} ne doit jamais
 * remonter une violation non rattrapée de {@code uk_compteurs_reference_admission_annee}
 * — ni sous course sur une ligne absente (couvert bout en bout par {@link
 * tg.novadigital.edukeys.admission.web.AdmissionIdempotenceConcurrenceIntegrationTest}),
 * ni lorsqu'une ligne existe déjà mais désactivée : la requête verrouillée
 * filtre {@code actif = true}, l'index unique est partiel sur la même
 * condition, une nouvelle ligne active doit donc pouvoir être créée sans
 * jamais entrer en conflit avec la ligne désactivée.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GenerateurReferenceAdmissionIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GenerateurReferenceAdmission generateurReferenceAdmission;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void genereUneReference_quandUneLigneDeCompteurExisteMaisEstDesactivee() {
        UUID etablissementId = UUID.fromString(creerEtablissement("CPTD"));
        int annee = java.time.Year.now().getValue();
        jdbcTemplate.update(
                "insert into compteurs_reference_admission "
                        + "(id, etablissement_id, annee, dernier, actif, date_desactivation, date_creation, date_modification) "
                        + "values (gen_random_uuid(), ?::uuid, ?, 42, false, now(), now(), now())",
                etablissementId, annee);

        String reference;
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            reference = generateurReferenceAdmission.genererReference(etablissementId);
        }

        assertThat(reference).isEqualTo("PRE-%d-000001".formatted(annee));
        Long nombreDeLignesActives = jdbcTemplate.queryForObject(
                "select count(*) from compteurs_reference_admission where etablissement_id = ?::uuid and annee = ? and actif = true",
                Long.class, etablissementId, annee);
        assertThat(nombreDeLignesActives).isEqualTo(1L);
    }

    // La course sur une ligne de compteur absente (point 2, cas principal) est
    // couverte bout en bout par AdmissionIdempotenceConcurrenceIntegrationTest
    // (deuxPremieresSoumissionsSimultanees_...), avec de vraies requêtes HTTP
    // concurrentes sur un vrai serveur Tomcat : genererReference() exige une
    // transaction déjà ouverte (MANDATORY), ce qui exclut de l'exercer
    // directement depuis des threads nus sans passer par le conteneur.

    private String creerEtablissement(String prefixeCode) {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = prefixeCode + System.nanoTime() % 100000;

        try {
            String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonSuperAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"%s","nom":"Établissement %s","typeEtablissement":"COLLEGE",
                                     "ville":"Lomé","email":"contact.%s@edukeys.tg",
                                     "emailAdministrateur":"admin.%s@edukeys.tg","nomCompletAdministrateur":"Admin Test"}
                                    """.formatted(code, code, code.toLowerCase(), code.toLowerCase())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(reponseEtab, "$.etablissement.id");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String connecterEtObtenirAccessToken(String email) {
        try {
            String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"identifiant":"%s","motDePasse":"%s"}
                                    """.formatted(email, MOT_DE_PASSE)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(reponseLogin, "$.accessToken");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
