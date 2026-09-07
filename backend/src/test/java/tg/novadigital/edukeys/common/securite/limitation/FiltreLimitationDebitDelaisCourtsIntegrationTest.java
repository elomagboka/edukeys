package tg.novadigital.edukeys.common.securite.limitation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vérifie l'exigence "aucun verrouillage définitif" de l'issue #58 avec un
 * délai plafond réduit à quelques dizaines de millisecondes (surcharge de
 * {@link LimitationDebitProperties} via {@code properties}), pour ne jamais
 * dépendre d'une véritable attente de plusieurs secondes/minutes dans les
 * tests : passé le plafond configuré ici, une nouvelle tentative doit à
 * nouveau être acceptée — l'attente s'écoule, elle ne bloque jamais
 * indéfiniment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "edukeys.securite.limitation-debit.par-compte.delai-initial=50ms",
        "edukeys.securite.limitation-debit.par-compte.delai-plafond=50ms",
        "edukeys.securite.limitation-debit.par-ip.delai-initial=50ms",
        "edukeys.securite.limitation-debit.par-ip.delai-plafond=50ms",
        // Seuil par IP abaissé de 150 à 5 : le seuil réel est volontairement
        // large (IP mutualisées togolaises) et l'atteindre en HTTP coûterait
        // 150 BCrypt. C'est le mécanisme qu'on teste ici, pas la valeur.
        "edukeys.securite.limitation-debit.par-ip.seuil-tolerance=5"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FiltreLimitationDebitDelaisCourtsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FiltreLimitationDebit filtreLimitationDebit;

    @BeforeEach
    void reinitialiserLaLimitationDeDebit() {
        filtreLimitationDebit.reinitialiserPourLesTests();
    }

    private static final String EMAIL_DIRECTEUR = "directeur@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @Test
    void nePasVerrouillerDefinitivement_laTentativeSuivanteRepasseUneFoisLattenteEcoulee() throws Exception {
        String emailCible = EMAIL_DIRECTEUR;

        for (int i = 0; i < 4; i++) {
            tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        }

        // Seuil dépassé : la tentative immédiate est bloquée.
        tenterLogin(emailCible, "mauvais-mot-de-passe").andExpect(status().isTooManyRequests());

        // Le délai plafond configuré ici (50 ms) est bref : on attend qu'il
        // s'écoule réellement, sans dépendre d'un minutage exact — c'est le
        // point de l'exigence "pas de verrouillage définitif", pas la durée
        // précise de l'attente (déjà couverte en unitaire sur le compteur).
        attendre(200);

        tenterLogin(emailCible, MOT_DE_PASSE).andExpect(status().isOk());
    }

    private void attendre(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private org.springframework.test.web.servlet.ResultActions tenterLogin(String email, String motDePasse) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","motDePasse":"%s"}
                        """.formatted(email, motDePasse)));
    }

    /**
     * Le compteur par IP déclenche seul, sur des emails tous différents : le
     * compteur par compte reste à un échec chacun, seul le balayage explique
     * le 429. C'est le scénario que l'issue #58 invoque pour justifier ce
     * second compteur — un mot de passe très courant essayé sur des centaines
     * de comptes.
     */
    @Test
    void declencheLeCompteurParIp_sansDeclencherLeCompteurParCompte_quandLesEmailsVarient() throws Exception {
        for (int i = 0; i < 6; i++) {
            String email = "balayage." + i + "." + java.util.UUID.randomUUID() + "@edukeys.tg";
            tenterLogin(email, "peu-importe").andExpect(status().isUnauthorized());
        }

        // Email encore jamais vu : son compteur par compte est à zéro, seul le
        // compteur par IP peut expliquer le 429.
        String dernierEmail = "balayage.dernier." + java.util.UUID.randomUUID() + "@edukeys.tg";
        tenterLogin(dernierEmail, "peu-importe").andExpect(status().isTooManyRequests());
    }
}
