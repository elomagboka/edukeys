package tg.novadigital.edukeys.etablissement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Test d'intégration de la bascule de site principal (R4) : exactement un
 * site principal actif à la fois par établissement, vérifié EN BASE (pas
 * seulement via la réponse HTTP), après une bascule vers un nouveau site.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SiteControllerIntegrationTest {

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

    @Test
    void designerPrincipal_bascule_UnSeulSitePrincipalActifEnBase() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = "STE" + System.nanoTime() % 100000;

        String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Sites","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String etablissementId = JsonPath.read(reponseEtab, "$.id");

        // SiteController est gardé par ETABLISSEMENT_GERER (ADMIN, borné à
        // son propre établissement) depuis le durcissement T-10 (2e revue) :
        // SUPER_ADMIN ne la porte plus, un ADMIN affecté à cet établissement
        // est requis pour les opérations sur les sites.
        String jeton = creerAdminEtObtenirToken(etablissementId);

        String reponseSite = mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/sites")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ANNEXE1","nom":"Annexe 1","ville":"Lomé"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String siteAnnexeId = JsonPath.read(reponseSite, "$.id");

        mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/sites/" + siteAnnexeId + "/principal")
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isNoContent());

        // Requête SQL directe : MockMvc partage la transaction de test mais pas
        // automatiquement le flush Hibernate — sans lui, JdbcTemplate verrait
        // encore l'état non flushé (Session en flush-mode AUTO, jamais déclenché
        // par une requête JDBC brute).
        entityManager.flush();

        var lignesPrincipales = jdbcTemplate.queryForList(
                "select id from sites where etablissement_id = ?::uuid and principal = true and actif = true",
                etablissementId);
        assertThat(lignesPrincipales).hasSize(1);
        assertThat(lignesPrincipales.get(0).get("id").toString()).isEqualTo(siteAnnexeId);

        mockMvc.perform(get("/api/v1/etablissements/" + etablissementId + "/sites")
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isOk());
    }

    /**
     * BLOQUANT 2 (durcissement post-revue T-10) : le compteur dénormalisé
     * {@code etablissements.nombre_sites_actifs} doit rester égal, en
     * permanence, à un décompte réel des sites actifs — vérifié EN BASE par
     * JDBC direct, pas seulement via la réponse HTTP, après chaque opération
     * qui le fait varier (création puis désactivation d'un site).
     */
    @Test
    void nombreSitesActifs_resteCoherentAvecUnDecompteReel_apresCreationEtDesactivation() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = "CNT" + System.nanoTime() % 100000;

        String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Compteur","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String etablissementId = JsonPath.read(reponseEtab, "$.id");
        entityManager.flush();

        // SiteController est gardé par ETABLISSEMENT_GERER (ADMIN seul depuis
        // le durcissement T-10, 2e revue) : un ADMIN affecté à cet
        // établissement est requis pour créer/désactiver ses sites.
        String jeton = creerAdminEtObtenirToken(etablissementId);

        // Après création : site principal seul -> compteur et décompte réel valent 1.
        assertCompteurCoherentAvecLeDecompteReel(etablissementId);

        String reponseSite = mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/sites")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ANNEXE-CNT","nom":"Annexe Compteur","ville":"Lomé"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String siteAnnexeId = JsonPath.read(reponseSite, "$.id");
        entityManager.flush();

        // Après création d'un second site : compteur et décompte réel valent 2.
        assertCompteurCoherentAvecLeDecompteReel(etablissementId);

        mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/sites/" + siteAnnexeId + "/desactivation")
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isNoContent());
        entityManager.flush();

        // Après désactivation du site annexe : compteur et décompte réel reviennent à 1.
        assertCompteurCoherentAvecLeDecompteReel(etablissementId);
    }

    private void assertCompteurCoherentAvecLeDecompteReel(String etablissementId) {
        Integer compteurDenormalise = jdbcTemplate.queryForObject(
                "select nombre_sites_actifs from etablissements where id = ?::uuid", Integer.class, etablissementId);
        Integer decompteReel = jdbcTemplate.queryForObject(
                "select count(*) from sites where etablissement_id = ?::uuid and actif = true", Integer.class, etablissementId);
        assertThat(compteurDenormalise).isEqualTo(decompteReel);
    }

    /** R11 : un ADMIN qui n'est pas affecté à l'établissement cible reçoit 404 sur GET et POST des sites. */
    @Test
    void refuse404UnAdmin_surLesSitesDUnEtablissementDifferentDuSien() throws Exception {
        String jeton = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = "AUT" + System.nanoTime() % 100000;
        String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Autre","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String autreEtablissementId = JsonPath.read(reponseEtab, "$.id");

        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00.sites." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Sites Test", false));
        entityManager.flush();

        UUID etablissementAdmin = UUID.randomUUID();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, 'COLLEGE', 'Lomé', ?, true, now(), now())",
                etablissementAdmin, "ADS-" + etablissementAdmin, "Établissement Admin Sites Test",
                "ads." + etablissementAdmin + "@edukeys.tg");
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementAdmin);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        String jetonAdmin = connecterEtObtenirAccessToken(compteAdmin.getEmail());

        mockMvc.perform(get("/api/v1/etablissements/" + autreEtablissementId + "/sites")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/etablissements/" + autreEtablissementId + "/sites")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ANNEXE2","nom":"Annexe 2","ville":"Lomé"}
                                """))
                .andExpect(status().isNotFound());
    }

    /** Crée un compte ADMIN affecté à l'établissement donné et retourne son jeton d'accès (SiteController exige ETABLISSEMENT_GERER, pas ETABLISSEMENT_CREER). */
    private String creerAdminEtObtenirToken(String etablissementId) throws Exception {
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00.sites." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Sites Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        return connecterEtObtenirAccessToken(compteAdmin.getEmail());
    }

    /**
     * Durcissement T-10 (2e revue) : SUPER_ADMIN ne porte plus
     * {@code ETABLISSEMENT_GERER} (RoleCode), donc reçoit désormais 403 sur
     * les endpoints de sites — auparavant accessibles, contrairement à
     * l'intention de cloisonner ces opérations à l'ADMIN de l'établissement.
     */
    @Test
    void refuse403UnSuperAdmin_surLesEndpointsDeSites() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = "SUP" + System.nanoTime() % 100000;

        String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Super Admin","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String etablissementId = JsonPath.read(reponseEtab, "$.id");

        mockMvc.perform(get("/api/v1/etablissements/" + etablissementId + "/sites")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/sites")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ANNEXE-SUP","nom":"Annexe Super Admin","ville":"Lomé"}
                                """))
                .andExpect(status().isForbidden());
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
