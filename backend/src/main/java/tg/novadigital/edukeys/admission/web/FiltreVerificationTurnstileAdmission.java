package tg.novadigital.edukeys.admission.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tg.novadigital.edukeys.admission.service.VerificationTurnstileService;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.securite.reseau.FiltreAdresseIpCliente;
import tg.novadigital.edukeys.common.web.CorrelationIdFilter;

/**
 * Vérifie Cloudflare Turnstile (règle 4 de la spec US-06, revue B2+I1)
 * <b>avant toute lecture du corps</b> et <b>avant l'analyse multipart</b> par
 * Tomcat : le jeton voyage dans l'en-tête {@code CF-Turnstile-Response},
 * jamais dans la partie JSON du formulaire, précisément pour que ce filtre
 * puisse refuser sans toucher au flux d'entrée (celui-ci reste intact pour
 * {@code multipart.resolve-lazily} et pour {@code FiltreLimitationDebit}, qui
 * ne lit plus jamais le corps de cette route — B1).
 *
 * <p>Jeton absent, invalide, ou service Turnstile indisponible ⇒ refus
 * immédiat (422, même forme que {@code GestionnaireExceptionsGlobal} pour
 * {@link tg.novadigital.edukeys.common.exception.RegleMetierViolee}), jamais
 * de passage silencieux — et surtout jamais dans une transaction : ce filtre
 * s'exécute hors du {@code DispatcherServlet}, avant toute ouverture de
 * connexion à la base.</p>
 *
 * <p><strong>Ordre</strong> : après {@link tg.novadigital.edukeys.common.securite.limitation.FiltreLimitationDebit}
 * — ce dernier ne lit de toute façon jamais le corps de cette route (B1, I3 :
 * seul le compteur IP dédié à la famille admission s'applique), et le placer
 * en premier permet à un jeton Turnstile invalide ou absent d'alimenter
 * lui-même le compteur d'échecs par IP (429 après répétition), au lieu de le
 * court-circuiter systématiquement. Toujours avant {@link FiltreAdresseIpCliente}
 * n'aurait pas de sens : l'IP transmise à Turnstile doit déjà être la vraie
 * IP cliente, donc toujours après {@code ForwardedHeaderFilter}.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class FiltreVerificationTurnstileAdmission extends OncePerRequestFilter {

    private static final String CHEMIN_PROTEGE = "/api/v1/public/etablissements/*/demandes-admission";
    private static final String EN_TETE_JETON = "CF-Turnstile-Response";
    private static final AntPathMatcher CHEMIN_MATCHER = new AntPathMatcher();

    private final ObjectProvider<VerificationTurnstileService> verificationTurnstileService;
    private final ObjectMapper objectMapper;

    /**
     * {@link ObjectProvider} plutôt qu'une injection directe de
     * {@link VerificationTurnstileService} (bean applicatif ordinaire, ni
     * configuration ni type {@code Filter}) : les tranches {@code @WebMvcTest}
     * sans rapport avec l'admission n'incluent pas ce bean dans leur contexte,
     * mais détectent quand même ce filtre (tout bean {@code Filter} est
     * auto-inclus par Spring Boot dans une tranche MVC) — une injection
     * directe ferait échouer leur démarrage pour une dépendance qu'elles
     * n'utilisent jamais ; {@code @Lazy} seul ne suffit pas non plus, la
     * définition du bean étant absente du contexte, pas seulement non encore
     * instanciée.
     */
    public FiltreVerificationTurnstileAdmission(
            ObjectProvider<VerificationTurnstileService> verificationTurnstileService, ObjectMapper objectMapper) {
        this.verificationTurnstileService = verificationTurnstileService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !CHEMIN_MATCHER.match(CHEMIN_PROTEGE, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String jeton = request.getHeader(EN_TETE_JETON);
        String adresseIp = FiltreAdresseIpCliente.adresseIpDe(request);
        VerificationTurnstileService service = verificationTurnstileService.getIfAvailable();

        if (service == null || !service.verifier(jeton, adresseIp)) {
            repondreRefus(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void repondreRefus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("type", "about:blank");
        corps.put("title", HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        corps.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        corps.put("detail", "Vérification anti-robot échouée.");
        corps.put("instance", request.getRequestURI());
        corps.put("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUT_REQUETE));
        corps.put("code", CodeErreur.ADMISSION_CAPTCHA_ECHEC.name());

        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Octets écrits directement, jamais getWriter() : même raisonnement que
        // FiltreLimitationDebit#repondre429 (charset, accents).
        response.getOutputStream().write(objectMapper.writeValueAsBytes(corps));
    }
}
