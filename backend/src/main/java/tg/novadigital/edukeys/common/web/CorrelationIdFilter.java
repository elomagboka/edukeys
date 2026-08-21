package tg.novadigital.edukeys.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Attribue à chaque requête un identifiant de corrélation — réutilisé s'il
 * est fourni par l'appelant — présent dans les logs (MDC) et dans la réponse,
 * pour relier un incident signalé par un utilisateur aux lignes de log
 * correspondantes.
 */
@Component
// +10, pas HIGHEST_PRECEDENCE nu : Boot enregistre son ForwardedHeaderFilter
// (server.forward-headers-strategy=framework) sur Ordered.HIGHEST_PRECEDENCE
// exactement, et il doit s'exécuter en premier pour que request.getRemoteAddr()
// soit déjà réécrit quand ce filtre (et JwtAuthenticationFilter derrière)
// s'exécutent — une même valeur d'ordre ne garantit aucun ordre relatif.
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String EN_TETE_CORRELATION_ID = "X-Correlation-Id";
    public static final String CLE_MDC = "correlationId";
    public static final String ATTRIBUT_REQUETE = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(EN_TETE_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        request.setAttribute(ATTRIBUT_REQUETE, correlationId);
        response.setHeader(EN_TETE_CORRELATION_ID, correlationId);
        MDC.put(CLE_MDC, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CLE_MDC);
        }
    }
}
