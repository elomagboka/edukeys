package tg.novadigital.edukeys.identite.security;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PerimetreEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Ouvre le contexte multi-établissement pour la durée de la requête, à partir
 * du seul {@link UtilisateurPrincipal} déjà authentifié par
 * {@link JwtAuthenticationFilter} (donc enregistré <strong>après</strong> lui
 * dans la chaîne, voir {@code SecurityConfig}).
 *
 * <p>Source unique : le claim {@code eta} du JWT. Jamais un en-tête HTTP
 * {@code X-Etablissement-Id} — un tel en-tête serait falsifiable par le
 * client, ce qui romprait tout le cloisonnement de sécurité (ADR-0002).</p>
 *
 * <p>Vit dans le module {@code identite}, pas {@code common} : il lit
 * {@link UtilisateurPrincipal}, propre à {@code identite}, et
 * {@code common → identite} violerait la règle d'architecture n°1
 * (aucune dépendance croisée entre modules métier).</p>
 *
 * <p>Fermeture en {@code finally} obligatoire : Tomcat réutilise ses threads
 * entre requêtes, un contexte non refermé fuiterait vers la requête
 * suivante traitée par le même thread.</p>
 */
public class ContexteEtablissementFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        PorteeEtablissement portee = null;

        if (authentication != null && authentication.getPrincipal() instanceof UtilisateurPrincipal principal
                && principal.etablissementId() != null) {
            portee = ContexteEtablissement.ouvrir(principal.etablissementId(), PerimetreEtablissement.Origine.JETON);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (portee != null) {
                portee.close();
            }
        }
    }
}
