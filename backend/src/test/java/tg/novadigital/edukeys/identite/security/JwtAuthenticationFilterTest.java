package tg.novadigital.edukeys.identite.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link JwtAuthenticationFilter} peuple le {@code SecurityContext} avant tout
 * {@code @PreAuthorize} : une erreur ici (mauvais préfixe d'autorité, jeton
 * invalide non intercepté) compromettrait l'autorisation de toute
 * l'application, silencieusement (correction T-04, lot 2 n°7).
 */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final PermissionResolver permissionResolver = mock(PermissionResolver.class);
    private final JwtAuthenticationFilter filtre = new JwtAuthenticationFilter(jwtService, permissionResolver);

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain filterChain = mock(FilterChain.class);

    private Logger loggerSecurite;
    private Level niveauInitialSecurite;
    private ListAppender<ILoggingEvent> appenderSecurite;

    @BeforeEach
    void capturerLogsSecurite() {
        loggerSecurite = (Logger) LoggerFactory.getLogger("SECURITE");
        niveauInitialSecurite = loggerSecurite.getLevel();
        loggerSecurite.setLevel(Level.DEBUG);
        appenderSecurite = new ListAppender<>();
        appenderSecurite.start();
        loggerSecurite.addAppender(appenderSecurite);
    }

    @AfterEach
    void nettoyerLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
        loggerSecurite.detachAppender(appenderSecurite);
        loggerSecurite.setLevel(niveauInitialSecurite);
    }

    @Test
    void nAuthentifiePersonne_quandAucunEnTeteAuthorization() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filtre.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nAuthentifiePersonne_quandEnTeteSansPrefixeBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filtre.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).analyser(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authentifieAvecLesAutoritesAttendues_quandJetonValide() throws Exception {
        UUID utilisateurId = UUID.randomUUID();
        UtilisateurPrincipal principal = new UtilisateurPrincipal(utilisateurId, null, Set.of("ENSEIGNANT", "PARENT"));

        when(request.getHeader("Authorization")).thenReturn("Bearer un-jeton-valide");
        when(jwtService.analyser("un-jeton-valide")).thenReturn(principal);
        when(permissionResolver.resoudrePermissions(Set.of("ENSEIGNANT", "PARENT")))
                .thenReturn(Set.of("NOTE_SAISIR", "ENFANT_CONSULTER"));

        filtre.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_ENSEIGNANT", "ROLE_PARENT", "NOTE_SAISIR", "ENFANT_CONSULTER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void videLeContexteEtLaisseContinuerLaChaine_quandJetonInvalide() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer jeton-invalide");
        when(jwtService.analyser("jeton-invalide")).thenThrow(new io.jsonwebtoken.security.SignatureException("signature invalide"));

        filtre.doFilterInternal(request, response, filterChain);

        // Aucune exception ne doit remonter : c'est la règle anyRequest().authenticated()
        // de SecurityConfig, en aval, qui traduira l'absence d'authentification en 401.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Un rejet silencieux (correction précédente) rend un balayage de
     * signatures forgées invisible. Le motif doit désormais apparaître dans
     * les logs — mais jamais le jeton lui-même, quelle que soit sa valeur.
     */
    @Test
    void journaliseLeMotifDuRejet_maisJamaisLeJeton() throws Exception {
        String jetonSecret = "eyJhbGciOiJIUzI1NiJ9.jeton-avec-signature-forgee";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jetonSecret);
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(jwtService.analyser(jetonSecret)).thenThrow(new io.jsonwebtoken.security.SignatureException("signature invalide"));

        filtre.doFilterInternal(request, response, filterChain);

        assertThat(appenderSecurite.list).hasSize(1);
        ILoggingEvent evenement = appenderSecurite.list.get(0);
        assertThat(evenement.getFormattedMessage()).contains("motif=signature_invalide", "ip=203.0.113.7");
        assertThat(evenement.getFormattedMessage()).doesNotContain(jetonSecret);
        // Le niveau est la garantie de visibilité en production : un balayage
        // de signatures forgées passé en DEBUG (niveau souvent désactivé) est
        // aussi invisible qu'un rejet non journalisé du tout — c'est le défaut
        // que cette correction visait précisément (relecture T-04, repasse n°3).
        assertThat(evenement.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
    }

    @Test
    void journaliseUnJetonExpireCommeTel_enDebugCarFrequentEtAttendu() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer jeton-expire");
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(jwtService.analyser("jeton-expire")).thenThrow(
                new io.jsonwebtoken.ExpiredJwtException(null, null, "jeton expiré"));

        filtre.doFilterInternal(request, response, filterChain);

        assertThat(appenderSecurite.list).hasSize(1);
        ILoggingEvent evenement = appenderSecurite.list.get(0);
        assertThat(evenement.getFormattedMessage()).contains("motif=jeton_expire");
        // Seul motif volontairement en DEBUG et pas WARN, contrairement aux
        // autres motifs (cf. test ci-dessus) : un access token expiré après
        // 15 minutes d'inactivité est attendu, pas un incident.
        assertThat(evenement.getLevel()).isEqualTo(ch.qos.logback.classic.Level.DEBUG);
    }

    @Test
    void neReanalysePasLeJeton_quandDejaAuthentifie() throws Exception {
        UtilisateurPrincipal dejaAuthentifie = new UtilisateurPrincipal(UUID.randomUUID(), null, Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(dejaAuthentifie, null, Set.of()));
        when(request.getHeader("Authorization")).thenReturn("Bearer un-jeton-quelconque");

        filtre.doFilterInternal(request, response, filterChain);

        verify(jwtService, never()).analyser(any());
        verify(filterChain).doFilter(request, response);
    }
}
