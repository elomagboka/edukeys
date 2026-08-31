package tg.novadigital.edukeys.etablissement.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

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
 * Test d'intégration bout-en-bout de la tranche verticale US-00 (T-10),
 * contre un conteneur PostgreSQL éphémère migré par Flyway. Le point vérifié
 * ici — pas seulement le contrat HTTP — est le piège central signalé par la
 * spec : la création d'un établissement crée réellement son site principal
 * dans la même transaction (le contexte multi-établissement doit être ouvert
 * explicitement pour que la persistance du site ne lève pas
 * {@code ContexteEtablissementAbsentException}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EtablissementControllerIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String EMAIL_DIRECTEUR = "directeur@edukeys.tg";
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
    void creeUnEtablissement_avecSonSitePrincipal_dansLaMemeTransaction() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = "NEW" + System.nanoTime() % 100000;

        String reponse = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Nouvel Établissement","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))
                .andExpect(jsonPath("$.referentielInitialise").value(true))
                .andReturn().getResponse().getContentAsString();

        String etablissementId = JsonPath.read(reponse, "$.id");

        // GET .../sites n'est plus vérifié via HTTP ici : ce endpoint est
        // gardé par ETABLISSEMENT_GERER (ADMIN, borné à son propre
        // établissement), qu'un SUPER_ADMIN ne porte plus depuis le
        // durcissement T-10 (2e revue) — SiteController/LogoController sont
        // l'organisation métier interne d'un établissement (ADR-0005), pas
        // l'administration de plateforme. La preuve passe uniquement par la
        // base ci-dessous.

        // Preuve EN BASE (requête SQL directe, pas seulement la réponse HTTP) que le site principal
        // existe réellement et porte le bon etablissement_id — le piège central de T-10 (A1) :
        // sans ContexteEtablissement.ouvrir(...) dans EtablissementService#creer, cette ligne n'existerait pas.
        var lignes = jdbcTemplate.queryForList(
                "select code, principal, etablissement_id, actif from sites where etablissement_id = ?::uuid",
                etablissementId);
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).get("code")).isEqualTo(code.toUpperCase() + "-PRINCIPAL");
        assertThat(lignes.get(0).get("principal")).isEqualTo(true);
        assertThat(lignes.get(0).get("etablissement_id").toString()).isEqualTo(etablissementId);
        assertThat(lignes.get(0).get("actif")).isEqualTo(true);
    }

    @Test
    void refuse409_quandLeCodeEstDejaUtiliseParUnEtablissementActif() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CSJ","nom":"Doublon","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"doublon@edukeys.tg"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void refuse403_quandUnCompteSansPermissionCreeUnEtablissement() throws Exception {
        String jetonDirecteur = connecterEtObtenirAccessToken(EMAIL_DIRECTEUR);

        mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonDirecteur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"XYZ","nom":"Refuse","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"refuse@edukeys.tg"}
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * R11 : un ADMIN n'accède qu'à son propre établissement — 404, pas 403,
     * sur un identifiant différent, pour ne pas révéler l'existence d'un
     * autre établissement.
     */
    @Test
    void refuse404UnAdmin_surUnEtablissementDifferentDuSien() throws Exception {
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Test", false));
        entityManager.flush();

        UUID etablissementAdmin = UUID.randomUUID();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, 'COLLEGE', 'Lomé', ?, true, now(), now())",
                etablissementAdmin, "ADM-" + etablissementAdmin, "Établissement Admin Test", "adm." + etablissementAdmin + "@edukeys.tg");
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementAdmin);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        String jetonAdmin = connecterEtObtenirAccessToken(compteAdmin.getEmail());

        // Son propre établissement : accessible.
        mockMvc.perform(get("/api/v1/etablissements/" + etablissementAdmin)
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());

        // Établissement de démonstration CSJ : hors de son périmètre -> 404.
        mockMvc.perform(get("/api/v1/etablissements/01977000-0000-7000-9000-000000000001")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());
    }

    /**
     * R11 sur l'endpoint historique spécifiquement (distinct du GET détail) :
     * un ADMIN qui consulte l'historique d'un établissement qui n'est pas le
     * sien reçoit 404, pas 403 — ne pas révéler l'existence d'un autre
     * établissement, y compris via cet endpoint.
     */
    @Test
    void refuse404UnAdmin_surLHistoriqueDUnEtablissementDifferentDuSien() throws Exception {
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00.hist." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Historique Test", false));
        entityManager.flush();

        UUID etablissementAdmin = UUID.randomUUID();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, 'COLLEGE', 'Lomé', ?, true, now(), now())",
                etablissementAdmin, "ADH-" + etablissementAdmin, "Établissement Admin Historique Test",
                "adh." + etablissementAdmin + "@edukeys.tg");
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementAdmin);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        String jetonAdmin = connecterEtObtenirAccessToken(compteAdmin.getEmail());

        // Son propre établissement : accessible (200, même si liste vide).
        mockMvc.perform(get("/api/v1/etablissements/" + etablissementAdmin + "/historique")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());

        // Établissement de démonstration CSJ, hors périmètre : 404 (pas 403).
        mockMvc.perform(get("/api/v1/etablissements/01977000-0000-7000-9000-000000000001/historique")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse401_quandAppeleSansJeton() throws Exception {
        mockMvc.perform(get("/api/v1/etablissements"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * BLOQUANT 1 (durcissement post-revue T-10) : {@code GET /etablissements}
     * retourne toutes les lignes de la plateforme (Etablissement échappe au
     * filtre Hibernate) — réservé à SUPER_ADMIN (ETABLISSEMENT_CREER), jamais
     * à ADMIN (ETABLISSEMENT_GERER seul).
     */
    @Test
    void superAdminPeutListerTouteLaPlateforme() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu").isArray());
    }

    /**
     * 403 (pas 404) : refuser une permission n'est pas la même chose que
     * cacher l'existence d'une ressource identifiée par {id} — ici il n'y a
     * pas d'{id} dans le chemin, c'est un pur refus de permission.
     */
    @Test
    void refuse403_quandUnAdminListeTouteLaPlateforme() throws Exception {
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00.liste." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Liste Test", false));
        entityManager.flush();

        UUID etablissementAdmin = UUID.randomUUID();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, 'COLLEGE', 'Lomé', ?, true, now(), now())",
                etablissementAdmin, "ADL-" + etablissementAdmin, "Établissement Admin Liste Test",
                "adl." + etablissementAdmin + "@edukeys.tg");
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementAdmin);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        String jetonAdmin = connecterEtObtenirAccessToken(compteAdmin.getEmail());

        mockMvc.perform(get("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isForbidden());
    }

    /**
     * BLOQUANT 1 : nouveau point d'entrée ADMIN — {@code GET /courant} ne
     * prend aucun {id} dans le chemin, résout l'établissement depuis le
     * contexte multi-établissement ouvert par le JWT de l'appelant.
     */
    @Test
    void unAdminRecoitSonPropreEtablissement_viaCourant() throws Exception {
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00.courant." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Courant Test", false));
        entityManager.flush();

        UUID etablissementAdmin = UUID.randomUUID();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, 'COLLEGE', 'Lomé', ?, true, now(), now())",
                etablissementAdmin, "ADC-" + etablissementAdmin, "Établissement Admin Courant Test",
                "adc." + etablissementAdmin + "@edukeys.tg");
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementAdmin);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        String jetonAdmin = connecterEtObtenirAccessToken(compteAdmin.getEmail());

        mockMvc.perform(get("/api/v1/etablissements/courant")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(etablissementAdmin.toString()));
    }

    /**
     * BLOQUANT 2 : la désactivation en cascade d'un établissement désactive
     * tous ses sites — le compteur dénormalisé doit retomber à 0, cohérent
     * avec un décompte réel exécuté directement en base.
     */
    @Test
    void nombreSitesActifs_retombeA0_apresDesactivationEnCascadeDeLEtablissement() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = "CAS" + System.nanoTime() % 100000;

        String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Cascade","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String etablissementId = JsonPath.read(reponseEtab, "$.id");

        // Site secondaire inséré directement en base (JDBC), pas via
        // SiteController : ce endpoint est gardé par ETABLISSEMENT_GERER
        // (ADMIN seul depuis le durcissement T-10, 2e revue), que le
        // SUPER_ADMIN utilisé ici ne porte plus.
        jdbcTemplate.update(
                "insert into sites (id, etablissement_id, code, nom, principal, ville, actif, date_creation, date_modification) "
                        + "values (gen_random_uuid(), ?::uuid, 'ANNEXE-CAS', 'Annexe Cascade', false, 'Lomé', true, now(), now())",
                etablissementId);
        // Le compteur dénormalisé nombre_sites_actifs n'est incrémenté que par
        // SiteService#creer : l'insertion JDBC directe ci-dessus le maintient
        // à jour manuellement pour que la cascade de désactivation (qui
        // décrémente une fois par site actif trouvé) retombe bien à 0.
        jdbcTemplate.update(
                "update etablissements set nombre_sites_actifs = nombre_sites_actifs + 1 where id = ?::uuid",
                etablissementId);
        entityManager.flush();
        // L'Etablissement chargé par le POST de création reste dans le
        // premier niveau de cache Hibernate de cette transaction de test :
        // sans clear(), la désactivation ci-dessous relirait ce Java object
        // en mémoire (compteur = 1) plutôt que la ligne mise à jour
        // directement par JDBC ci-dessus (compteur = 2), et décompterait deux
        // fois depuis 1 -> valeur négative refusée par l'entité.
        entityManager.clear();

        mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/desactivation")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();

        Integer compteurDenormalise = jdbcTemplate.queryForObject(
                "select nombre_sites_actifs from etablissements where id = ?::uuid", Integer.class, etablissementId);
        Integer decompteReel = jdbcTemplate.queryForObject(
                "select count(*) from sites where etablissement_id = ?::uuid and actif = true", Integer.class, etablissementId);
        assertThat(compteurDenormalise).isZero();
        assertThat(decompteReel).isZero();
        assertThat(compteurDenormalise).isEqualTo(decompteReel);
    }

    /**
     * IMPORTANT 2 (durcissement post-revue T-10) : {@code reactiver} doit
     * restaurer symétriquement ce que {@code desactiver} a désactivé en
     * cascade — sans quoi un établissement réactivé revient sans site
     * principal actif, violant R4 (« tout établissement actif a exactement
     * un site principal actif »).
     */
    @Test
    void reactivation_restaureLeSitePrincipalActifEtLeLogo_apresDesactivationEnCascade() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = "REA" + System.nanoTime() % 100000;

        String reponseEtab = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Réactivation","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String etablissementId = JsonPath.read(reponseEtab, "$.id");

        // Site secondaire + logo insérés directement en base (SiteController/
        // LogoController ne sont plus accessibles à SUPER_ADMIN, voir le test
        // précédent).
        jdbcTemplate.update(
                "insert into sites (id, etablissement_id, code, nom, principal, ville, actif, date_creation, date_modification) "
                        + "values (gen_random_uuid(), ?::uuid, 'ANNEXE-REA', 'Annexe Réactivation', false, 'Lomé', true, now(), now())",
                etablissementId);
        jdbcTemplate.update(
                "update etablissements set nombre_sites_actifs = nombre_sites_actifs + 1 where id = ?::uuid",
                etablissementId);
        jdbcTemplate.update(
                "insert into logos_etablissement (id, etablissement_id, nom_fichier, type_mime, taille_octets, empreinte_sha256, contenu, actif, date_creation, date_modification) "
                        + "values (gen_random_uuid(), ?::uuid, 'logo.png', 'image/png', 3, 'empreinte', E'\\\\x010203', true, now(), now())",
                etablissementId);
        entityManager.flush();
        // Voir le commentaire équivalent du test précédent : sans clear(),
        // l'Etablissement en cache de premier niveau (compteur = 1, issu du
        // POST de création) désynchronise la cascade de désactivation du
        // compteur réellement mis à jour en base ci-dessus (2).
        entityManager.clear();

        mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/desactivation")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();

        mockMvc.perform(post("/api/v1/etablissements/" + etablissementId + "/reactivation")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isNoContent());
        entityManager.flush();

        Integer sitesActifs = jdbcTemplate.queryForObject(
                "select count(*) from sites where etablissement_id = ?::uuid and actif = true", Integer.class, etablissementId);
        Integer sitesPrincipauxActifs = jdbcTemplate.queryForObject(
                "select count(*) from sites where etablissement_id = ?::uuid and actif = true and principal = true",
                Integer.class, etablissementId);
        Integer logosActifs = jdbcTemplate.queryForObject(
                "select count(*) from logos_etablissement where etablissement_id = ?::uuid and actif = true", Integer.class, etablissementId);
        Integer compteurDenormalise = jdbcTemplate.queryForObject(
                "select nombre_sites_actifs from etablissements where id = ?::uuid", Integer.class, etablissementId);

        // R4 : exactement un site principal actif après réactivation.
        assertThat(sitesPrincipauxActifs).isEqualTo(1);
        assertThat(sitesActifs).isEqualTo(2);
        assertThat(logosActifs).isEqualTo(1);
        assertThat(compteurDenormalise).isEqualTo(sitesActifs);
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
