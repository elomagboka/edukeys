package tg.novadigital.edukeys.common.securite.limitation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Règle 5 de CLAUDE.md : la limitation par compte doit voir le MÊME identifiant que le contrôleur, quel que soit
 * l'encodage du corps (le convertisseur Spring passe les octets bruts à Jackson, qui détecte UTF-8/16/32), et les
 * requêtes ambiguës (charset non Unicode déclaré, champ JSON dupliqué) ne doivent ni échapper au compteur ni
 * provoquer de 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LimitationDebitEncodagesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FiltreLimitationDebit filtreLimitationDebit;

    @BeforeEach
    void reinitialiser() {
        filtreLimitationDebit.reinitialiserPourLesTests();
    }

    private ResultActions login(byte[] corps, String adresseIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps)
                .with(requete -> {
                    requete.setRemoteAddr(adresseIp);
                    return requete;
                }));
    }

    private static byte[] json(String identifiant, Charset charset, boolean avecBom) {
        String texte = "{\"identifiant\":\"" + identifiant + "\",\"motDePasse\":\"mauvais\"}";
        byte[] octets = texte.getBytes(charset);
        if (!avecBom) {
            return octets;
        }
        // Java écrit lui-même le BOM pour "UTF-16" ; pour les autres, on le préfixe à la main (UTF-16BE : FE FF).
        byte[] bom = charset.equals(StandardCharsets.UTF_16BE) ? new byte[] {(byte) 0xFE, (byte) 0xFF} : new byte[0];
        byte[] resultat = new byte[bom.length + octets.length];
        System.arraycopy(bom, 0, resultat, 0, bom.length);
        System.arraycopy(octets, 0, resultat, bom.length, octets.length);
        return resultat;
    }

    private void verifierLimiteParCompte(String libelle, Charset charset, boolean avecBom) throws Exception {
        String cible = "cible-" + libelle + "-" + UUID.randomUUID();
        // Seuil par compte : 3 échecs tolérés. Chaque tentative vient d'une IP différente : seule la clé de compte
        // peut déclencher. Les 401 prouvent que le contrôleur a bien lu l'identifiant dans cet encodage.
        for (int i = 0; i < 4; i++) {
            login(json(cible, charset, avecBom), "10.20." + libelle.length() + "." + (i + 1)).andExpect(status().isUnauthorized());
        }
        login(json(cible, charset, avecBom), "10.21.0.99").andExpect(status().isTooManyRequests());
        // Le même compte en UTF-8 est bloqué par le même compteur.
        login(json(cible, StandardCharsets.UTF_8, false), "10.21.0.100").andExpect(status().isTooManyRequests());
    }

    @Test
    void utf16LESansBom_estLimiteParCompte() throws Exception {
        verifierLimiteParCompte("u16le", StandardCharsets.UTF_16LE, false);
    }

    @Test
    void utf16BEAvecBom_estLimiteParCompte() throws Exception {
        verifierLimiteParCompte("u16be-bom", StandardCharsets.UTF_16BE, true);
    }

    @Test
    void utf16AvecBomEcritParJava_estLimiteParCompte() throws Exception {
        verifierLimiteParCompte("u16", StandardCharsets.UTF_16, false);
    }

    @Test
    void utf32LE_estLimiteParCompte() throws Exception {
        verifierLimiteParCompte("u32le", Charset.forName("UTF-32LE"), false);
    }

    @Test
    void charsetNonUnicodeDeclare_estRefuseEn415_surLesCheminsLimites() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json;charset=ISO-8859-1")
                        .content("{\"identifiant\":\"x\",\"motDePasse\":\"y\"}".getBytes(StandardCharsets.ISO_8859_1)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void champJsonDuplique_estRefuseEn400_jamais500_etNAuthentifiePas() throws Exception {
        login("{\"identifiant\":\"a\",\"identifiant\":\"b\",\"motDePasse\":\"x\"}".getBytes(StandardCharsets.UTF_8), "10.30.0.1")
                .andExpect(status().isBadRequest());
        // Même avec un identifiant et un mot de passe valides, un doublon est rejeté (pas de « dernier gagne »).
        login("{\"identifiant\":\"directeur@edukeys.tg\",\"motDePasse\":\"Password123!\",\"identifiant\":\"zzz\"}"
                .getBytes(StandardCharsets.UTF_8), "10.30.0.2").andExpect(status().isBadRequest());
        login("{\"identifiant\":\"directeur@edukeys.tg\",\"email\":\"x\",\"motDePasse\":\"Password123!\",\"motDePasse\":\"autre\"}"
                .getBytes(StandardCharsets.UTF_8), "10.30.0.3").andExpect(status().isBadRequest());
    }

    private ResultActions loginAvecContentType(String contentType, byte[] corps, String adresseIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(contentType)
                .content(corps)
                .with(requete -> {
                    requete.setRemoteAddr(adresseIp);
                    return requete;
                }));
    }

    @Test
    void charsetUtf16DeclareAvecCorpsUtf16_estAccepte_etLimiteParCompte() throws Exception {
        String cible = "cible-decl16-" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            loginAvecContentType("application/json;charset=UTF-16", json(cible, StandardCharsets.UTF_16, false), "10.40.0." + (i + 1))
                    .andExpect(status().isUnauthorized());
        }
        loginAvecContentType("application/json;charset=UTF-16", json(cible, StandardCharsets.UTF_16, false), "10.41.0.1")
                .andExpect(status().isTooManyRequests());
        login(json(cible, StandardCharsets.UTF_8, false), "10.41.0.2").andExpect(status().isTooManyRequests());
    }

    @Test
    void charsetUtf8DeclareMaisCorpsUtf16_n_echappePasAuCompteur() throws Exception {
        // Le contrôleur et le filtre détectent tous deux l'encodage réel : jamais un 500, et la clé de compte est vue.
        String cible = "cible-menteur-" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            loginAvecContentType("application/json;charset=UTF-8", json(cible, StandardCharsets.UTF_16LE, false), "10.42.0." + (i + 1))
                    .andExpect(status().is4xxClientError());
        }
        login(json(cible, StandardCharsets.UTF_8, false), "10.43.0.1").andExpect(status().isTooManyRequests());
    }

    @Test
    void corpsVideOuJsonInvalide_sur_login_donne400_jamais500() throws Exception {
        login(new byte[0], "10.44.0.1").andExpect(status().isBadRequest());
        login("{pas du json".getBytes(StandardCharsets.UTF_8), "10.44.0.2").andExpect(status().isBadRequest());
        login("[]".getBytes(StandardCharsets.UTF_8), "10.44.0.3").andExpect(status().isBadRequest());
        login("null".getBytes(StandardCharsets.UTF_8), "10.44.0.4").andExpect(status().isBadRequest());
        login(new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00}, "10.44.0.5").andExpect(status().isBadRequest());
    }

    @Test
    void champEmailAliasDuplique_estRefuseEn400() throws Exception {
        login("{\"email\":\"a\",\"email\":\"b\",\"motDePasse\":\"x\"}".getBytes(StandardCharsets.UTF_8), "10.45.0.1")
                .andExpect(status().isBadRequest());
    }
}
