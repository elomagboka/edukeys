package tg.novadigital.edukeys.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultHandler;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 3e revue, point 4 : la soumission publique d'admission renvoie désormais un
 * {@code DeferredResult} (le plancher de temps de réponse ne bloque plus le
 * thread servlet). MockMvc n'exécute alors pas la requête de façon
 * synchrone : sans {@code asyncDispatch}, {@code .andExpect(status()...)}
 * échoue sur le statut intermédiaire de la requête encore en cours. Ce
 * point d'entrée unique évite de répéter ce détail dans chaque test.
 */
public final class AsyncMockMvcSupport {

    private AsyncMockMvcSupport() {
    }

    public static ResultActions performerEtResoudre(MockMvc mockMvc, RequestBuilder requestBuilder) throws Exception {
        MvcResult resultat = mockMvc.perform(requestBuilder).andReturn();
        if (resultat.getRequest().isAsyncStarted()) {
            resultat.getAsyncResult(); // Bloque jusqu'à la résolution du DeferredResult (setResult/setErrorResult).
            resultat = mockMvc.perform(asyncDispatch(resultat)).andReturn();
        }
        MvcResult resultatFinal = resultat;
        return new ResultActions() {
            @Override
            public ResultActions andExpect(ResultMatcher matcher) throws Exception {
                matcher.match(resultatFinal);
                return this;
            }

            @Override
            public ResultActions andDo(ResultHandler handler) throws Exception {
                handler.handle(resultatFinal);
                return this;
            }

            @Override
            public MvcResult andReturn() {
                return resultatFinal;
            }
        };
    }
}
