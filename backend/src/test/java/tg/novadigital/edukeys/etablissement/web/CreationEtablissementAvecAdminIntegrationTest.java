package tg.novadigital.edukeys.etablissement.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * Preuve bout-en-bout de US-04 §3 : {@code POST /api/v1/etablissements} crée
 * l'établissement, son site principal ET son premier compte ADMIN dans une
 * seule transaction (voir {@code EtablissementService#creer}) — un
 * établissement sans administrateur est un état impossible, jamais un état à
 * réparer après coup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CreationEtablissementAvecAdminIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String connecterEtObtenirAccessToken(String email, String motDePasse) throws Exception {
        String reponseLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","motDePasse":"%s"}
                                """.formatted(email, motDePasse)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponseLogin, "$.accessToken");
    }

    @Test
    void creeLetablissementSonSiteEtSonCompteAdmin_puisLadminSeConnecteChangeSonMotDePasseEtGereLesComptes()
            throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN, MOT_DE_PASSE);
        String code = "BOOT" + System.nanoTime() % 100000;
        String emailAdmin = "admin.bootstrap." + code.toLowerCase() + "@edukeys.tg";

        String reponseCreation = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Bootstrap","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg",
                                 "emailAdministrateur":"%s","nomCompletAdministrateur":"Admin Bootstrap"}
                                """.formatted(code, code.toLowerCase(), emailAdmin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motDePasseTemporaireAdmin").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String etablissementId = JsonPath.read(reponseCreation, "$.etablissement.id");
        String motDePasseTemporaire = JsonPath.read(reponseCreation, "$.motDePasseTemporaireAdmin");

        // Preuve en base que le compte ADMIN existe réellement, avec une
        // affectation active portant le rôle ADMIN sur CET établissement.
        var lignes = jdbcTemplate.queryForList(
                """
                select u.email, a.actif from utilisateurs u
                join affectations_etablissement a on a.utilisateur_id = u.id
                join affectation_roles r on r.affectation_id = a.id
                where a.etablissement_id = ?::uuid and r.role_code = 'ADMIN'
                """,
                etablissementId);
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).get("email")).isEqualTo(emailAdmin);
        assertThat(lignes.get(0).get("actif")).isEqualTo(true);

        // L'admin se connecte réellement avec le mot de passe temporaire.
        String jetonAdmin = connecterEtObtenirAccessToken(emailAdmin, motDePasseTemporaire);

        // Tant qu'il n'a pas changé son mot de passe : refusé sur un endpoint métier.
        mockMvc.perform(get("/api/v1/utilisateurs/mon-etablissement")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOT_DE_PASSE_A_CHANGER"));

        mockMvc.perform(post("/api/v1/utilisateurs/moi/mot-de-passe")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ancienMotDePasse":"%s","nouveauMotDePasse":"NouveauMotDePasse456!"}
                                """.formatted(motDePasseTemporaire)))
                .andExpect(status().isNoContent());

        String jetonAdminActif = connecterEtObtenirAccessToken(emailAdmin, "NouveauMotDePasse456!");

        // L'admin peut désormais gérer les comptes de SON établissement :
        // preuve finale que la boucle de bootstrap est complète.
        mockMvc.perform(post("/api/v1/utilisateurs")
                        .header("Authorization", "Bearer " + jetonAdminActif)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"premier.enseignant.%s@edukeys.tg","nomComplet":"Premier Enseignant","roles":["ENSEIGNANT"]}
                                """.formatted(code.toLowerCase())))
                .andExpect(status().isCreated());
    }
}
