package tg.novadigital.edukeys.identite.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tg.novadigital.edukeys.common.securite.JournalSecurite;

/**
 * Authentifie chaque requête à partir du header {@code Authorization: Bearer}.
 * Les {@link GrantedAuthority} sont construites à partir des permissions
 * dérivées des rôles portés par le JWT (jamais des permissions elles-mêmes,
 * absentes du jeton — arbitrage T-04 n°5), préfixées {@code ROLE_} pour les
 * rôles et laissées telles quelles pour les permissions afin de permettre
 * {@code hasRole(...)} et {@code hasAuthority(...)} indifféremment.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXE_EN_TETE = "Bearer ";

    private final JwtService jwtService;
    private final PermissionResolver permissionResolver;

    public JwtAuthenticationFilter(JwtService jwtService, PermissionResolver permissionResolver) {
        this.jwtService = jwtService;
        this.permissionResolver = permissionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String enTete = request.getHeader("Authorization");
        if (enTete != null && enTete.startsWith(PREFIXE_EN_TETE) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String jeton = enTete.substring(PREFIXE_EN_TETE.length());
            try {
                UtilisateurPrincipal principal = jwtService.analyser(jeton);
                Set<String> permissions = permissionResolver.resoudrePermissions(principal.codesRoles());

                List<GrantedAuthority> autorites = java.util.stream.Stream
                        .concat(
                                principal.codesRoles().stream().map(role -> "ROLE_" + role),
                                permissions.stream())
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                var authentication = new UsernamePasswordAuthenticationToken(principal, null, autorites);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
                JournalSecurite.rejetJeton(motifRejet(e), request.getRemoteAddr());
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Ne renvoie jamais le jeton ni le message brut de l'exception : seule la catégorie du rejet est journalisée. */
    private static String motifRejet(RuntimeException e) {
        return switch (e) {
            case ExpiredJwtException ignored -> "jeton_expire";
            case SignatureException ignored -> "signature_invalide";
            case MalformedJwtException ignored -> "jeton_malforme";
            case UnsupportedJwtException ignored -> "jeton_non_supporte";
            default -> "jeton_invalide";
        };
    }
}
