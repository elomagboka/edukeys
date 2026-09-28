package tg.novadigital.edukeys.admission.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Tests d'intégration US-06 : validation des pièces jointes (magic bytes,
 * PDF au contenu actif, limites de taille et de nombre), atomicité d'une
 * soumission avec pièce rejetée, et validation du choix niveau/classe.
 * Limites de taille abaissées via {@link DynamicPropertySource} pour des
 * tests rapides, sans dépendre des valeurs par défaut de production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfigurationTurnstileDoubleTest.class)
@Transactional
class AdmissionValidationEtLimitesIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void abaisserLesLimites(DynamicPropertyRegistry registry) {
        registry.add("edukeys.admission.taille-max-piece", () -> "10KB");
        registry.add("edukeys.admission.taille-max-total-pieces", () -> "18KB");
        registry.add("edukeys.admission.nombre-max-pieces", () -> "2");
    }

    @BeforeEach
    void reinitialiserLeDouble() {
        VerificationTurnstileServiceDouble.reinitialiser();
    }

    private int nombreDeDossiers(String etablissementId) {
        Long n = jdbcTemplate.queryForObject(
                "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, etablissementId);
        return n == null ? 0 : n.intValue();
    }

    private int nombreDePieces(String etablissementId) {
        Long n = jdbcTemplate.queryForObject(
                "select count(*) from pieces_jointes_admission p join demandes_admission d on d.id = p.demande_id "
                        + "where d.etablissement_id = ?::uuid", Long.class, etablissementId);
        return n == null ? 0 : n.intValue();
    }

    // ------------------------------------------------------------------
    // Magic bytes, pas extension ni Content-Type (critère 4)
    // ------------------------------------------------------------------

    @Test
    void refuseUnSvg_malgreUneExtensionPdfEtUnContentTypePdfDeclares() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("SVG");
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8);
        soumettreAvecUnePiece(ctx, svg, "acte.pdf", "application/pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_FORMAT_NON_SUPPORTE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuseUnFichierOffice_malgreUneExtensionPdfDeclaree() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("OFF");
        // Signature ZIP (docx/xlsx sont des zip) : 'PK\x03\x04'.
        byte[] office = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        soumettreAvecUnePiece(ctx, office, "acte.pdf", "application/pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_FORMAT_NON_SUPPORTE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuseUnExecutableRenommeEnPdf() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("EXE");
        // Signature MZ des exécutables Windows.
        byte[] exe = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        soumettreAvecUnePiece(ctx, exe, "acte.pdf", "application/pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_FORMAT_NON_SUPPORTE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuse422_fichierVide() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("VIDE");
        soumettreAvecUnePiece(ctx, new byte[0], "acte.pdf", "application/pdf")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_VIDE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuse415_pdfContenantDuJavaScript() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("PDFJS");
        byte[] pdf = "%PDF-1.4\n<< /JavaScript (app.alert('x')) >>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        soumettreAvecUnePiece(ctx, pdf, "acte.pdf", "application/pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_CONTENU_SUSPECT"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuse415_pdfContenantUnLaunch() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("PDFLA");
        byte[] pdf = "%PDF-1.4\n<< /Launch (cmd.exe) >>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        soumettreAvecUnePiece(ctx, pdf, "acte.pdf", "application/pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_CONTENU_SUSPECT"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    // ------------------------------------------------------------------
    // Limites de nombre et de taille (règle 7 de la spec US-06)
    // ------------------------------------------------------------------

    @Test
    void refuse422_depassementDuNombreMaxDePieces() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("NBMAX");
        MockMultipartFile demandePart = construireDemandeJson(ctx);
        MockMultipartFile p1 = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        MockMultipartFile p2 = new MockMultipartFile("pieces", "photo.jpg", "image/jpeg", jpegMinimal());
        MockMultipartFile p3 = new MockMultipartFile("pieces", "autre.pdf", "application/pdf", pdfMinimal());

        tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                        .file(demandePart).file(p1).file(p2).file(p3)
                        .param("typesPieces", "ACTE_NAISSANCE").param("typesPieces", "PHOTO").param("typesPieces", "BULLETIN")
                        .header("CF-Turnstile-Response", "jeton-valide"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_TROP_DE_PIECES"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuse422_depassementDeLaTailleParPiece() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("TMAX");
        byte[] trop = pdfDeTaille(11 * 1024); // > 10 Ko (limite abaissée pour le test)
        soumettreAvecUnePiece(ctx, trop, "acte.pdf", "application/pdf")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_TROP_VOLUMINEUSE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuse422_depassementDeLaTailleTotaleCumulee() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("TTOT");
        MockMultipartFile demandePart = construireDemandeJson(ctx);
        // Deux pièces de 10 Ko chacune (juste sous la limite unitaire) : 20 Ko > 18 Ko cumulés.
        MockMultipartFile p1 = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfDeTaille(9 * 1024 + 500));
        MockMultipartFile p2 = new MockMultipartFile("pieces", "photo.jpg", "image/jpeg", jpegDeTaille(9 * 1024 + 500));

        tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                        .file(demandePart).file(p1).file(p2)
                        .param("typesPieces", "ACTE_NAISSANCE").param("typesPieces", "PHOTO")
                        .header("CF-Turnstile-Response", "jeton-valide"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_TAILLE_TOTALE_PIECES_DEPASSEE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    // ------------------------------------------------------------------
    // 3e revue, point 5 : même fichier joint deux fois -> 422 explicite, jamais 500
    // ------------------------------------------------------------------

    @Test
    void refuse422_memeFichierJointDeuxFoisDansLaMemeSoumission() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("DUPPIECE");
        MockMultipartFile demandePart = construireDemandeJson(ctx);
        MockMultipartFile acte = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        // Même contenu (donc même empreinte SHA-256), nom différent : geste banal sur mobile.
        MockMultipartFile memeActeAutreNom = new MockMultipartFile("pieces", "acte-copie.pdf", "application/pdf", pdfMinimal());

        tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc,
                        multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                                .file(demandePart).file(acte).file(memeActeAutreNom)
                                .param("typesPieces", "ACTE_NAISSANCE").param("typesPieces", "AUTRE")
                                .header("CF-Turnstile-Response", "jeton-valide"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_PIECE_DUPLIQUEE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    // ------------------------------------------------------------------
    // Acte de naissance obligatoire (critère 4)
    // ------------------------------------------------------------------

    @Test
    void refuse422_absenceDeLActeDeNaissance() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("SANSACTE");
        MockMultipartFile demandePart = construireDemandeJson(ctx);
        MockMultipartFile piece = new MockMultipartFile("pieces", "photo.jpg", "image/jpeg", jpegMinimal());

        tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                        .file(demandePart).file(piece).param("typesPieces", "PHOTO")
                        .header("CF-Turnstile-Response", "jeton-valide"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_ACTE_NAISSANCE_MANQUANT"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    // ------------------------------------------------------------------
    // Atomicité (critère 3) : une pièce rejetée => ni dossier ni autre pièce en base
    // ------------------------------------------------------------------

    @Test
    void nEnregistreNiDossierNiAucunePiece_quandUnePieceEstRejetee() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("ATOM");
        MockMultipartFile demandePart = construireDemandeJson(ctx);
        MockMultipartFile bonneActe = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        MockMultipartFile mauvaisePhoto = new MockMultipartFile("pieces", "photo.svg", "image/svg+xml",
                "<svg></svg>".getBytes(StandardCharsets.UTF_8));

        tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                        .file(demandePart).file(bonneActe).file(mauvaisePhoto)
                        .param("typesPieces", "ACTE_NAISSANCE").param("typesPieces", "PHOTO")
                        .header("CF-Turnstile-Response", "jeton-valide"))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
        assertThat(nombreDePieces(ctx.etablissementId())).isZero();
    }

    // ------------------------------------------------------------------
    // Choix niveau/classe (critère 5)
    // ------------------------------------------------------------------

    @Test
    void refuse422_classeHorsNiveauChoisi() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("CLHN");
        String autreNiveauId = creerNiveau(ctx.jetonAdmin(), "5ème", "CLHN2N", 2, ctx.cycleId());
        String classeAutreNiveauId = creerClasse(ctx.jetonAdmin(), autreNiveauId);

        soumettreAvecChoix(ctx, ctx.niveauId(), classeAutreNiveauId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_CHOIX_NIVEAU_CLASSE_INVALIDE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuse422_niveauDesactive() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("NVINACT");
        mockMvc.perform(post("/api/v1/niveaux/" + ctx.niveauId() + "/desactivation")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin()))
                .andExpect(status().isNoContent());

        soumettreAvecChoix(ctx, ctx.niveauId(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_CHOIX_NIVEAU_CLASSE_INVALIDE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    @Test
    void refuse422_classeDesactivee() throws Exception {
        Contexte ctx = preparerEtablissementEtOffre("CLINACT");
        String classeId = creerClasse(ctx.jetonAdmin(), ctx.niveauId());
        mockMvc.perform(post("/api/v1/classes/" + classeId + "/desactivation")
                        .header("Authorization", "Bearer " + ctx.jetonAdmin()))
                .andExpect(status().isNoContent());

        soumettreAvecChoix(ctx, ctx.niveauId(), classeId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ADMISSION_CHOIX_NIVEAU_CLASSE_INVALIDE"));
        assertThat(nombreDeDossiers(ctx.etablissementId())).isZero();
    }

    // ------------------------------------------------------------------
    // Aides
    // ------------------------------------------------------------------

    private record Contexte(String etablissementId, String code, String jetonAdmin, String niveauId, String cycleId) {
    }

    private org.springframework.test.web.servlet.ResultActions soumettreAvecUnePiece(
            Contexte ctx, byte[] contenu, String nomFichier, String contentType) throws Exception {
        MockMultipartFile demandePart = construireDemandeJson(ctx);
        MockMultipartFile piece = new MockMultipartFile("pieces", nomFichier, contentType, contenu);
        return tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                .header("CF-Turnstile-Response", "jeton-valide"));
    }

    private org.springframework.test.web.servlet.ResultActions soumettreAvecChoix(
            Contexte ctx, String niveauId, String classeId) throws Exception {
        MockMultipartFile demandePart = construireDemandeJsonAvecChoix(ctx, niveauId, classeId);
        MockMultipartFile piece = new MockMultipartFile("pieces", "acte.pdf", "application/pdf", pdfMinimal());
        return tg.novadigital.edukeys.testsupport.AsyncMockMvcSupport.performerEtResoudre(mockMvc, multipart("/api/v1/public/etablissements/" + ctx.code() + "/demandes-admission")
                .file(demandePart).file(piece).param("typesPieces", "ACTE_NAISSANCE")
                .header("CF-Turnstile-Response", "jeton-valide"));
    }

    private MockMultipartFile construireDemandeJson(Contexte ctx) {
        return construireDemandeJsonAvecChoix(ctx, ctx.niveauId(), null);
    }

    private MockMultipartFile construireDemandeJsonAvecChoix(Contexte ctx, String niveauId, String classeId) {
        String classeJson = classeId == null ? "null" : "\"" + classeId + "\"";
        String json = """
                {
                  "demande": {
                    "niveauId": "%s",
                    "classeId": %s,
                    "nom": "Test",
                    "prenoms": "Validation",
                    "dateNaissance": "2015-01-01",
                    "lieuNaissance": "Lomé",
                    "sexe": "F",
                    "nationalite": "TG",
                    "responsableNom": "Responsable",
                    "responsablePrenoms": "Test",
                    "responsableLien": "PERE",
                    "responsableTelephone": "+22890000000",
                    "responsableEmail": "responsable@example.com",
                    "consentementDonnees": true
                  },
                  "siteWeb": null
                }
                """.formatted(niveauId, classeJson);
        return new MockMultipartFile("demande", "demande.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] pdfMinimal() {
        return "%PDF-1.4\n1 0 obj <<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] jpegMinimal() {
        byte[] contenu = new byte[100];
        contenu[0] = (byte) 0xFF;
        contenu[1] = (byte) 0xD8;
        contenu[2] = (byte) 0xFF;
        return contenu;
    }

    private static byte[] pdfDeTaille(int octets) {
        byte[] entete = "%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1);
        byte[] contenu = new byte[Math.max(octets, entete.length)];
        System.arraycopy(entete, 0, contenu, 0, entete.length);
        return contenu;
    }

    private static byte[] jpegDeTaille(int octets) {
        byte[] contenu = new byte[Math.max(octets, 3)];
        contenu[0] = (byte) 0xFF;
        contenu[1] = (byte) 0xD8;
        contenu[2] = (byte) 0xFF;
        return contenu;
    }

    private String creerClasse(String jetonAdmin, String niveauId) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/classes")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"niveauId":"%s"}
                                """.formatted(niveauId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private String creerCycle(String jetonAdmin, String libelle, String code, int rang) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/cycles")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s","rang":%d}
                                """.formatted(libelle, code, rang)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private String creerNiveau(String jetonAdmin, String libelle, String code, int rang, String cycleId) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/niveaux")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"libelle":"%s","code":"%s","rang":%d,"cycleId":"%s"}
                                """.formatted(libelle, code, rang, cycleId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.id");
    }

    private void creerEtActiverAnneeScolaire(String jetonAdmin) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/annees-scolaires")
                        .header("Authorization", "Bearer " + jetonAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dateDebut":"2026-09-01","dateFin":"2027-07-15"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(reponse, "$.id");
        mockMvc.perform(post("/api/v1/annees-scolaires/" + id + "/activation")
                        .header("Authorization", "Bearer " + jetonAdmin))
                .andExpect(status().isOk());
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
        tg.novadigital.edukeys.identite.domain.Utilisateur compteAdmin = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "admin.us06val." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Admin US-06 Validation Test", false));
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

    private Contexte preparerEtablissementEtOffre(String prefixeCode) throws Exception {
        String etablissementId = creerEtablissement(prefixeCode);
        String code = jdbcTemplate.queryForObject("select code from etablissements where id = ?::uuid", String.class, etablissementId);
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        creerEtActiverAnneeScolaire(jetonAdmin);
        String cycleId = creerCycle(jetonAdmin, "Collège", prefixeCode + "C", 1);
        String niveauId = creerNiveau(jetonAdmin, "6ème", prefixeCode + "N", 1, cycleId);
        jdbcTemplate.update("update etablissements set admissions_ouvertes = true where id = ?::uuid", etablissementId);
        entityManager.flush();
        entityManager.clear();
        return new Contexte(etablissementId, code, jetonAdmin, niveauId, cycleId);
    }
}
