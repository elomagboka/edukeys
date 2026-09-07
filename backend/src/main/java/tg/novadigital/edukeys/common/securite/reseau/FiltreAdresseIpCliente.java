package tg.novadigital.edukeys.common.securite.reseau;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Calcule une fois par requête l'adresse IP cliente digne de confiance (voir
 * {@link ExtracteurAdresseIpCliente}) et l'expose en attribut de requête, à
 * destination de la limitation de débit et de la journalisation de sécurité.
 *
 * <p><strong>Ordre : premier de la chaîne, avant le
 * {@code ForwardedHeaderFilter} de Spring.</strong> C'est une contrainte
 * absolue, et non un détail de configuration : ce filtre <em>retire</em> les
 * en-têtes {@code X-Forwarded-*} de la requête après les avoir appliqués. Un
 * filtre placé derrière lui ne voit plus {@code X-Forwarded-For} du tout
 * (vérifié : l'en-tête ressort à {@code null}) et n'a donc aucun moyen de
 * refaire le calcul correctement. C'est la raison pour laquelle
 * {@code ConfigurationEnTetesTransferes} réenregistre le
 * {@code ForwardedHeaderFilter} de Boot à un ordre postérieur à celui-ci.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltreAdresseIpCliente extends OncePerRequestFilter {

    /** Attribut de requête portant l'IP cliente retenue. */
    public static final String ATTRIBUT_REQUETE = "adresseIpCliente";

    private static final String EN_TETE = "X-Forwarded-For";

    private final int nbProxysDeConfiance;

    public FiltreAdresseIpCliente(int nbProxysDeConfiance) {
        this.nbProxysDeConfiance = nbProxysDeConfiance;
    }

    /**
     * Lit l'IP cliente déposée par ce filtre. Repli sur
     * {@code request.getRemoteAddr()} si l'attribut est absent — un appelant
     * ne doit jamais se retrouver sans adresse du tout.
     */
    public static String adresseIpDe(HttpServletRequest request) {
        Object valeur = request.getAttribute(ATTRIBUT_REQUETE);
        return valeur instanceof String adresse ? adresse : request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        request.setAttribute(ATTRIBUT_REQUETE, ExtracteurAdresseIpCliente.extraire(
                request.getHeader(EN_TETE), request.getRemoteAddr(), nbProxysDeConfiance));
        filterChain.doFilter(request, response);
    }
}
