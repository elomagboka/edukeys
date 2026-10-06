package tg.novadigital.edukeys.etablissement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * US-08 : le code d'établissement entre dans le matricule des élèves et dans leur identifiant de connexion —
 * 2 à 10 lettres ou chiffres ASCII, sans tiret, espace ni accent, refusé à la création (400).
 * Le code est immuable (R2) : il n'existe aucun endpoint de modification à valider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CodeEtablissementIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String jetonSuperAdmin() throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifiant":"%s","motDePasse":"%s"}
                                """.formatted(EMAIL_SUPER_ADMIN, MOT_DE_PASSE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.accessToken");
    }

    private ResultActions creer(String jeton, String code, String suffixeEmail) throws Exception {
        return mockMvc.perform(post("/api/v1/etablissements")
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"%s","nom":"Établissement test","typeEtablissement":"COLLEGE","ville":"Lomé",
                         "email":"contact.%s@edukeys.tg","emailAdministrateur":"admin.%s@edukeys.tg","nomCompletAdministrateur":"Admin Test"}
                        """.formatted(code, suffixeEmail, suffixeEmail)));
    }

    @Test
    void refuseLesCodesAccentues_avecEspaceOuTiret_tropCourtsOuTropLongs() throws Exception {
        String jeton = jetonSuperAdmin();
        String[] fautifs = {"ÉCOLE", "CS J", "CSJ-1", "A", "ABCDEFGHIJK", "CSJ_1", " CSJ"};
        for (int i = 0; i < fautifs.length; i++) {
            creer(jeton, fautifs[i], "faute" + i)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
        }
        Long crees = jdbcTemplate.queryForObject("select count(*) from etablissements where email like 'contact.faute%'", Long.class);
        assertThat(crees).isZero();
    }

    @Test
    void accepteUnCodeValide_enMinusculesEtLeStockeEnMajuscules() throws Exception {
        String jeton = jetonSuperAdmin();

        creer(jeton, "esk9", "ok1").andExpect(status().isCreated());

        String code = jdbcTemplate.queryForObject("select code from etablissements where email = 'contact.ok1@edukeys.tg'", String.class);
        assertThat(code).isEqualTo("ESK9");
    }
}
