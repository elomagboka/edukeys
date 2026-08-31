package tg.novadigital.edukeys.etablissement.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
 * Logo d'établissement (US-00) : le type est déterminé par inspection des
 * magic bytes, jamais par le Content-Type déclaré (falsifiable) — 415 en cas
 * de désaccord ; 413 au-delà de 1 Mo ; {@code image/svg+xml} toujours refusé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LogoControllerIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";
    private static final byte[] SIGNATURE_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

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

    /** R11 : un ADMIN qui n'est pas affecté à l'établissement cible reçoit 404 sur GET/PUT/DELETE du logo. */
    @Test
    void refuse404UnAdmin_surLeLogoDUnEtablissementDifferentDuSien() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String autreEtablissementId = creerEtablissement(jetonSuperAdmin, "LOG5");

        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00.logo." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Logo Test", false));
        entityManager.flush();

        UUID etablissementAdmin = UUID.randomUUID();
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into etablissements (id, code, nom, type_etablissement, ville, email, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, 'COLLEGE', 'Lomé', ?, true, now(), now())",
                etablissementAdmin, "ADO-" + etablissementAdmin, "Établissement Admin Logo Test",
                "ado." + etablissementAdmin + "@edukeys.tg");
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementAdmin);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        String jetonAdmin = connecterEtObtenirAccessToken(compteAdmin.getEmail());

        mockMvc.perform(get("/api/v1/etablissements/" + autreEtablissementId + "/logo")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());

        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "logo.png", MediaType.IMAGE_PNG_VALUE, Arrays.copyOf(SIGNATURE_PNG, 100));
        mockMvc.perform(multipart("/api/v1/etablissements/" + autreEtablissementId + "/logo")
                        .file(fichier)
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .with(req -> {
                            req.setMethod("PUT");
                            return req;
                        }))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/etablissements/" + autreEtablissementId + "/logo")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse415_quandLeContentTypeMentSurLeFormatReel() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String etablissementId = creerEtablissement(jetonSuperAdmin, "LOG1");
        String jeton = creerAdminEtObtenirToken(etablissementId);

        byte[] contenuTexteBrut = "ceci n'est pas une image".getBytes();
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "logo.png", MediaType.IMAGE_PNG_VALUE, contenuTexteBrut);

        mockMvc.perform(multipart("/api/v1/etablissements/" + etablissementId + "/logo")
                        .file(fichier)
                        .header("Authorization", "Bearer " + jeton)
                        .with(req -> {
                            req.setMethod("PUT");
                            return req;
                        }))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void refuseSvg_memeAvecContentTypeCorrect() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String etablissementId = creerEtablissement(jetonSuperAdmin, "LOG2");
        String jeton = creerAdminEtObtenirToken(etablissementId);

        byte[] contenuSvg = "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes();
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "logo.svg", "image/svg+xml", contenuSvg);

        mockMvc.perform(multipart("/api/v1/etablissements/" + etablissementId + "/logo")
                        .file(fichier)
                        .header("Authorization", "Bearer " + jeton)
                        .with(req -> {
                            req.setMethod("PUT");
                            return req;
                        }))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void refuse413_quandLeFichierDepasseUnMegaOctet() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String etablissementId = creerEtablissement(jetonSuperAdmin, "LOG3");
        String jeton = creerAdminEtObtenirToken(etablissementId);

        byte[] contenuTropVolumineux = new byte[1_100_000];
        System.arraycopy(SIGNATURE_PNG, 0, contenuTropVolumineux, 0, SIGNATURE_PNG.length);
        Arrays.fill(contenuTropVolumineux, SIGNATURE_PNG.length, contenuTropVolumineux.length, (byte) 1);
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "gros-logo.png", MediaType.IMAGE_PNG_VALUE, contenuTropVolumineux);

        mockMvc.perform(multipart("/api/v1/etablissements/" + etablissementId + "/logo")
                        .file(fichier)
                        .header("Authorization", "Bearer " + jeton)
                        .with(req -> {
                            req.setMethod("PUT");
                            return req;
                        }))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void accepteLeLogo_quandLesMagicBytesCorrespondentAuPng() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String etablissementId = creerEtablissement(jetonSuperAdmin, "LOG4");
        String jeton = creerAdminEtObtenirToken(etablissementId);

        byte[] contenuPngValide = Arrays.copyOf(SIGNATURE_PNG, 100);
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "logo.png", MediaType.IMAGE_PNG_VALUE, contenuPngValide);

        mockMvc.perform(multipart("/api/v1/etablissements/" + etablissementId + "/logo")
                        .file(fichier)
                        .header("Authorization", "Bearer " + jeton)
                        .with(req -> {
                            req.setMethod("PUT");
                            return req;
                        }))
                .andExpect(status().isOk());
    }

    /**
     * Durcissement T-10 (2e revue) : SUPER_ADMIN ne porte plus
     * {@code ETABLISSEMENT_GERER} (RoleCode), donc reçoit désormais 403 sur
     * les endpoints de logo (GET/PUT/DELETE).
     */
    @Test
    void refuse403UnSuperAdmin_surLesEndpointsDeLogo() throws Exception {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String etablissementId = creerEtablissement(jetonSuperAdmin, "SUP");

        mockMvc.perform(get("/api/v1/etablissements/" + etablissementId + "/logo")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());

        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "logo.png", MediaType.IMAGE_PNG_VALUE, Arrays.copyOf(SIGNATURE_PNG, 100));
        mockMvc.perform(multipart("/api/v1/etablissements/" + etablissementId + "/logo")
                        .file(fichier)
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .with(req -> {
                            req.setMethod("PUT");
                            return req;
                        }))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/etablissements/" + etablissementId + "/logo")
                        .header("Authorization", "Bearer " + jetonSuperAdmin))
                .andExpect(status().isForbidden());
    }

    private String creerEtablissement(String jeton, String prefixe) throws Exception {
        String code = prefixe + System.nanoTime() % 100000;
        String reponse = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement Logo","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg"}
                                """.formatted(code, code.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    /** Crée un compte ADMIN affecté à l'établissement donné et retourne son jeton d'accès (LogoController exige ETABLISSEMENT_GERER, pas ETABLISSEMENT_CREER). */
    private String creerAdminEtObtenirToken(String etablissementId) throws Exception {
        Utilisateur compteAdmin = utilisateurRepository.save(new Utilisateur(
                "admin.us00.logo." + UUID.randomUUID() + "@edukeys.tg",
                passwordEncoder.encode(MOT_DE_PASSE), "Admin US-00 Logo Test", false));
        entityManager.flush();

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        return connecterEtObtenirAccessToken(compteAdmin.getEmail());
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
