package tg.novadigital.edukeys.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import tg.novadigital.edukeys.common.web.CorrelationIdFilter;

/**
 * Sécurité Spring exclue de ce test de slice : il vérifie uniquement le
 * mapping des exceptions métier vers RFC 7807, indépendamment du module
 * identite (T-04) qui sécurise le reste de l'application par défaut.
 */
@WebMvcTest(controllers = ExceptionDeDemoControleur.class,
        excludeAutoConfiguration = { SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class })
@Import({ GestionnaireExceptionsGlobal.class, CorrelationIdFilter.class })
class GestionnaireExceptionsGlobalTest {

    @Autowired
    private MockMvc mockMvc;

    private Logger loggerRacine;
    private ListAppender<ILoggingEvent> appenderRacine;

    @BeforeEach
    void capturerLogs() {
        loggerRacine = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appenderRacine = new ListAppender<>();
        appenderRacine.start();
        loggerRacine.addAppender(appenderRacine);
    }

    @AfterEach
    void detacherAppender() {
        loggerRacine.detachAppender(appenderRacine);
    }

    @Test
    void ressourceIntrouvable_renvoie404() throws Exception {
        mockMvc.perform(get("/test-exceptions/introuvable"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Ressource introuvable."))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void regleMetierViolee_renvoie422() throws Exception {
        mockMvc.perform(get("/test-exceptions/regle-metier"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Règle métier violée."));
    }

    @Test
    void conflit_renvoie409() throws Exception {
        mockMvc.perform(get("/test-exceptions/conflit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Conflit détecté."));
    }

    @Test
    void accesInterdit_renvoie403() throws Exception {
        mockMvc.perform(get("/test-exceptions/acces-interdit"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Accès interdit."));
    }

    @Test
    void autorisationRefusee_renvoie403() throws Exception {
        mockMvc.perform(get("/test-exceptions/autorisation-refusee"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void erreurInattendue_renvoie500() throws Exception {
        mockMvc.perform(get("/test-exceptions/autre-chose"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void validationEchouee_renvoie400AvecLesChampsInvalides() throws Exception {
        mockMvc.perform(post("/test-exceptions/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pas-un-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.champsInvalides[0]").value(containsString("email")));
    }

    @Test
    void corpsIllisible_renvoie400() throws Exception {
        mockMvc.perform(post("/test-exceptions/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("pas-du-json-valide"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * Un email mal formé peut être un mot de passe saisi dans le mauvais
     * champ (cas typique visé par {@code JournalSecurite}). La réponse liste
     * le champ en défaut, jamais la valeur soumise, et aucun log — ni le
     * corps de la requête ni {@code rejectedValue} — ne doit la faire fuiter.
     */
    @Test
    void neJournalisePasLaValeurRejetee_quandValidationEchoue() throws Exception {
        String valeurSensible = "MotDePasseSecret123!";

        String corpsReponse = mockMvc.perform(post("/test-exceptions/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(valeurSensible)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpsReponse).doesNotContain(valeurSensible);

        // Inclut la stack trace (event.getThrowableProxy()), pas seulement le
        // message formaté : c'est là qu'une valeur fuiterait si une régression
        // faisait retomber ce cas sur le catch-all, qui logue LOG.error(..., ex).
        List<String> messages = appenderRacine.list.stream()
                .map(evenement -> evenement.getFormattedMessage()
                        + " " + ThrowableProxyUtil.asString(evenement.getThrowableProxy()))
                .toList();

        assertThat(messages).noneMatch(message -> message.contains(valeurSensible));
    }
}
