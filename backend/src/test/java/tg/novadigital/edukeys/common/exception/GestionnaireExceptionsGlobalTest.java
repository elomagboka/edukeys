package tg.novadigital.edukeys.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tg.novadigital.edukeys.common.web.CorrelationIdFilter;

@WebMvcTest(controllers = ExceptionDeDemoControleur.class)
@Import({ GestionnaireExceptionsGlobal.class, CorrelationIdFilter.class })
class GestionnaireExceptionsGlobalTest {

    @Autowired
    private MockMvc mockMvc;

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
    void erreurInattendue_renvoie500() throws Exception {
        mockMvc.perform(get("/test-exceptions/autre-chose"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
