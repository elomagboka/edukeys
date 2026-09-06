package tg.novadigital.edukeys.common.securite.limitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test d'intégration bout-en-bout de {@link FiltreLimitationDebit} (issue
 * #58) contre {@code POST /api/v1/auth/login} : N échecs déclenchent un 429
 * porteur d'un {@code Retry-After}, et un utilisateur légitime qui se trompe
 * une ou deux fois n'est pas gêné (seuil de tolérance par compte à 3, voir
 * {@code application.yml}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FiltreLimitationDebitIntegrationTest {

    private static final String EMAIL_DIRECTEUR = "directeur@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FiltreLimitationDebit filtreLimitationDebit;

    @BeforeEach
    void reinitialiserLaLimitationDeDebit() {
        filtreLimitationDebit.reinitialiserPourLesTests();
    }

    @Test
    void nGeinePasLUtilisateurLegitime_apresUnOuDeuxMotsDePasseErrones() throws Exception {
        tenterLogin(EMAIL_DIRECTEUR, "mauvais-mot-de-passe-1").andExpect(status().isUnauthorized());
        tenterLogin(EMAIL_DIRECTEUR, "mauvais-mot-de-passe-2").andExpect(status().isUnauthorized());

        // Le troisième essai, avec le bon mot de passe, doit encore passer :
        // le seuil de tolérance par compte est 3, deux échecs ne le dépassent pas.
        tenterLogin(EMAIL_DIRECTEUR, "Password123!").andExpect(status().isOk());
    }

    @Test
    void declenche429AvecRetryAfter_apresDepassementDuSeuilParCompte() throws Exception {
        // Seuil de tolérance par compte : 3. Le 4e échec consécutif déclenche l'attente.
        String emailCible = "cible.limitation." + java.util.UUID.randomUUID() + "@edukeys.tg";
        for (int i = 0; i < 4; i++) {
            tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        }

        MvcResult resultat = tenterLogin(emailCible, "mauvais-mot-de-passe")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        String retryAfter = resultat.getResponse().getHeader("Retry-After");
        assertThat(Integer.parseInt(retryAfter)).isPositive();
        assertThat(resultat.getResponse().getContentAsString()).contains("correlationId");
        // Message indifférencié (issue #58, point 4) : jamais le motif interne (compte inconnu, mot de passe incorrect...).
        assertThat(resultat.getResponse().getContentAsString()).doesNotContain("motif");
    }

    @Test
    void bloqueAussiUnCompteInconnu_carLeCompteurSincrementeAvantToutAccesEnBase() throws Exception {
        // Le compteur par compte s'incrémente sur la seule valeur soumise, que le
        // compte existe ou non — sinon le 429 deviendrait un oracle d'énumération.
        String emailInconnu = "inconnu." + java.util.UUID.randomUUID() + "@edukeys.tg";
        for (int i = 0; i < 4; i++) {
            tenterLogin(emailInconnu, "peu-importe").andExpect(status().isUnauthorized());
        }

        tenterLogin(emailInconnu, "peu-importe").andExpect(status().isTooManyRequests());
    }

    /**
     * Exigence n°4 de l'issue #58 : un compte inexistant et un compte existant
     * doivent produire exactement la même séquence de statuts et le même
     * corps de réponse (au {@code correlationId} près, unique par requête)
     * pour le même nombre de tentatives — sinon le 429 lui-même devient un
     * moyen d'énumérer les comptes valides.
     */
    @Test
    void produitLaMemeSequenceDeStatutsEtLeMemeCorps_pourUnCompteInconnuEtUnCompteExistant() throws Exception {
        String emailInconnu = "inconnu.comparaison." + java.util.UUID.randomUUID() + "@edukeys.tg";
        String emailExistant = EMAIL_DIRECTEUR;

        java.util.List<Integer> statutsInconnu = new java.util.ArrayList<>();
        java.util.List<String> corpsInconnu = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MvcResult resultat = tenterLogin(emailInconnu, "peu-importe").andReturn();
            statutsInconnu.add(resultat.getResponse().getStatus());
            corpsInconnu.add(normaliserCorps(resultat.getResponse().getContentAsString()));
        }

        java.util.List<Integer> statutsExistant = new java.util.ArrayList<>();
        java.util.List<String> corpsExistant = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            MvcResult resultat = tenterLogin(emailExistant, "mauvais-mot-de-passe").andReturn();
            statutsExistant.add(resultat.getResponse().getStatus());
            corpsExistant.add(normaliserCorps(resultat.getResponse().getContentAsString()));
        }

        assertThat(statutsInconnu).isEqualTo(statutsExistant);
        // La dernière réponse est le 429 (seuil par compte 3, 5 tentatives) :
        // au correlationId près, la forme complète du corps doit être identique.
        assertThat(statutsInconnu.get(statutsInconnu.size() - 1)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(corpsInconnu.get(corpsInconnu.size() - 1)).isEqualTo(corpsExistant.get(corpsExistant.size() - 1));
    }

    /** Retire le champ {@code correlationId}, unique par requête, avant comparaison des corps. */
    private String normaliserCorps(String corps) {
        if (corps == null || corps.isBlank()) {
            return corps;
        }
        return corps.replaceAll("\"correlationId\"\\s*:\\s*\"[^\"]*\"", "\"correlationId\":\"_\"");
    }

    @Test
    void remetLeCompteurAZero_apresUneConnexionReussie() throws Exception {
        String emailCible = EMAIL_DIRECTEUR;
        tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());

        // Deux échecs sous le seuil de tolérance (3), puis une connexion réussie.
        tenterLogin(emailCible, MOT_DE_PASSE).andExpect(status().isOk());

        // Le compteur est reparti à zéro : deux nouveaux échecs ne déclenchent rien.
        tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        tenterLogin(emailCible, MOT_DE_PASSE).andExpect(status().isOk());
    }

    /**
     * Indépendance des deux compteurs (issue #58, "deux compteurs, pas un") :
     * en variant l'email à chaque tentative depuis la même IP (MockMvc, IP de
     * test fixe), on approche le seuil par IP (50) sans jamais déclencher le
     * seuil par compte (3, un seul échec par email distinct) — le compteur par
     * compte du dernier email essayé reste donc à zéro.
     */
    @Test
    void declencheLeCompteurParIp_sansDeclencherLeCompteurParCompte_quandLesEmailsVarient() throws Exception {
        // Seuil de tolérance par IP : 50 échecs tolérés. Le 51e échec impose
        // un délai, mais seulement visible à la tentative suivante (la
        // vérification se fait avant l'enregistrement de l'échec courant).
        for (int i = 0; i < 51; i++) {
            String email = "balayage." + i + "." + java.util.UUID.randomUUID() + "@edukeys.tg";
            tenterLogin(email, "peu-importe").andExpect(status().isUnauthorized());
        }

        // Requête suivante, avec un email encore jamais vu : son propre
        // compteur par compte est à zéro, seul le compteur par IP peut
        // expliquer le 429.
        String dernierEmail = "balayage.dernier." + java.util.UUID.randomUUID() + "@edukeys.tg";
        tenterLogin(dernierEmail, "peu-importe").andExpect(status().isTooManyRequests());
    }

    /**
     * Réciproque : le compteur par compte déclenche sur un seul email répété,
     * sans jamais approcher le seuil par IP (50) — quatre requêtes seulement.
     */
    @Test
    void declencheLeCompteurParCompte_bienEnDessousDuSeuilParIp() throws Exception {
        String emailCible = "cible.compte." + java.util.UUID.randomUUID() + "@edukeys.tg";
        for (int i = 0; i < 4; i++) {
            tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        }

        tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isTooManyRequests());
    }

    /**
     * Corps RFC 7807 de même forme que {@code GestionnaireExceptionsGlobal}
     * (voir {@code GestionnaireExceptionsGlobalTest}) : mêmes clés, même
     * type de contenu — ce filtre s'exécute hors du {@code DispatcherServlet}
     * et sérialise sa réponse à la main, il doit néanmoins rester
     * indiscernable pour un client HTTP.
     */
    @Test
    void renvoieUnCorpsRfc7807DeMemeFormeQueLeGestionnaireDexceptionsGlobal() throws Exception {
        String emailCible = "forme.corps." + java.util.UUID.randomUUID() + "@edukeys.tg";
        for (int i = 0; i < 4; i++) {
            tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        }

        MvcResult resultat = tenterLogin(emailCible, "mauvais-mot-de-passe")
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.correlationId").exists())
                .andReturn();

        // Même jeu de clés qu'une erreur applicative ordinaire (404, 422...) :
        // aucune clé additionnelle qui trahirait le mécanisme de limitation.
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> corps = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(resultat.getResponse().getContentAsString(), java.util.Map.class);
        assertThat(corps.keySet()).containsExactlyInAnyOrder(
                "type", "title", "status", "detail", "instance", "correlationId");
    }

    /**
     * {@code Retry-After} doit être un entier positif cohérent avec l'attente
     * annoncée par {@link CompteurAttenteCroissante} : seuil de tolérance 3,
     * le 4e échec impose le délai initial (1 s d'après {@code application.yml}),
     * arrondi au-dessus — jamais 0, jamais une valeur aberrante.
     */
    @Test
    void renvoieUnRetryAfterEntierPositifEtCoherentAvecLAttenteAnnoncee() throws Exception {
        String emailCible = "retry-after." + java.util.UUID.randomUUID() + "@edukeys.tg";
        for (int i = 0; i < 4; i++) {
            tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        }

        MvcResult resultat = tenterLogin(emailCible, "mauvais-mot-de-passe")
                .andExpect(status().isTooManyRequests())
                .andReturn();

        int retryAfter = Integer.parseInt(resultat.getResponse().getHeader("Retry-After"));
        // Délai initial 1 s, arrondi à l'entier supérieur : jamais plus de
        // quelques secondes pour le tout premier dépassement de seuil.
        assertThat(retryAfter).isBetween(1, 5);
    }

    /**
     * Preuve que {@code RequeteAvecCorpsMisEnCache} ne casse pas le flux :
     * un login réussi doit continuer de fonctionner à travers le filtre.
     */
    @Test
    void laisseLeControleurLireLeCorps_etReussitLeLogin_malgreLaMiseEnCache() throws Exception {
        tenterLogin(EMAIL_DIRECTEUR, MOT_DE_PASSE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    /** Un corps JSON syntaxiquement invalide doit être rejeté par le contrôleur (400), jamais provoquer un 500 dans le filtre. */
    @Test
    void neRenvoieJamais500_quandLeCorpsEstUnJsonMalforme() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ceci n'est pas du json"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Un corps qui dépasse {@code tailleMaxCorpsOctets} (4096) est traité
     * comme absent par le filtre : seul le compteur par IP s'applique, la
     * requête continue vers le contrôleur (pas de 500).
     */
    @Test
    void neRenvoieJamais500_quandLeCorpsDepasseLaTailleMaximale() throws Exception {
        String emailEnorme = "a".repeat(5000) + "@edukeys.tg";
        String corps = """
                {"email":"%s","motDePasse":"mauvais"}
                """.formatted(emailEnorme);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps))
                .andExpect(status().is4xxClientError())
                .andExpect(status().is(org.hamcrest.Matchers.not(500)));
    }

    /** {@code /refresh} est protégé lui aussi, avec une clé dérivée de l'empreinte du jeton présenté. */
    @Test
    void protegeAussiLendpointRefresh_avecUneCleDeriveeDuJetonPresente() throws Exception {
        String jetonBidon = "jeton-invalide-" + java.util.UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            tenterRefresh(jetonBidon).andExpect(status().isUnauthorized());
        }

        tenterRefresh(jetonBidon)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    /** Un endpoint absent de {@code cheminsProteges} ne déclenche jamais de 429, même après de nombreux échecs. */
    @Test
    void neProtegePasUnCheminHorsListe_memeApresDeNombreuxEchecs() throws Exception {
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/v1/auth/etablissement-actif"))
                    .andExpect(status().is4xxClientError())
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
    }

    private org.springframework.test.web.servlet.ResultActions tenterLogin(String email, String motDePasse) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","motDePasse":"%s"}
                        """.formatted(email, motDePasse)));
    }

    private org.springframework.test.web.servlet.ResultActions tenterRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(refreshToken)));
    }
}
