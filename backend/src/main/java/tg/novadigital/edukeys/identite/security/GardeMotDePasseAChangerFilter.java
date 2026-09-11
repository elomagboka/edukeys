package tg.novadigital.edukeys.identite.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Garde centrale du changement de mot de passe obligatoire au premier accès
 * (US-04) : tant que {@link UtilisateurPrincipal#motDePasseAChanger()} est
 * vrai, seules trois entrées restent accessibles — tout autre endpoint
 * métier reçoit un 403 explicite ici, une seule fois, jamais recopié
 * contrôleur par contrôleur :
 * <ul>
 *   <li>{@code /api/v1/auth/**} : login, refresh, bascule d'établissement —
 *       sans cela, personne ne pourrait même obtenir le jeton qui porte ce
 *       drapeau ;</li>
 *   <li>{@code GET /api/v1/utilisateurs/moi} : ce n'est pas un endpoint de
 *       données métier, c'est l'identité de l'appelant — l'écran de
 *       changement de mot de passe obligatoire doit pouvoir afficher qui est
 *       connecté ;</li>
 *   <li>{@code POST /api/v1/utilisateurs/moi/mot-de-passe} : la seule sortie
 *       de cet état, elle doit rester joignable tant qu'il dure.</li>
 * </ul>
 * Rien d'autre : notamment pas {@code GET /api/v1/utilisateurs/mon-etablissement}
 * ni aucun autre endpoint qui expose des données au-delà de l'appelant
 * lui-même.
 *
 * <p>Enregistrée après {@link JwtAuthenticationFilter} (qui construit
 * l'{@code Authentication}), et avant {@link ContexteEtablissementFilter} :
 * inutile d'ouvrir un contexte multi-établissement pour une requête que
 * cette garde va de toute façon refuser.</p>
 */
public class GardeMotDePasseAChangerFilter extends OncePerRequestFilter {

    private static final String PREFIXE_AUTH = "/api/v1/auth/";
    private static final String CHEMIN_PROFIL = "/api/v1/utilisateurs/moi";
    private static final String CHEMIN_CHANGEMENT_MOT_DE_PASSE = "/api/v1/utilisateurs/moi/mot-de-passe";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof UtilisateurPrincipal principal
                && principal.motDePasseAChanger()
                && !cheminAutorise(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Changement de mot de passe requis","status":403,\
                    "detail":"Le mot de passe temporaire doit être changé avant tout autre accès.",\
                    "code":"MOT_DE_PASSE_A_CHANGER"}""");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean cheminAutorise(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String methode = request.getMethod();

        if (uri.startsWith(PREFIXE_AUTH)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(methode) && CHEMIN_PROFIL.equals(uri)) {
            return true;
        }
        return "POST".equalsIgnoreCase(methode) && CHEMIN_CHANGEMENT_MOT_DE_PASSE.equals(uri);
    }
}
