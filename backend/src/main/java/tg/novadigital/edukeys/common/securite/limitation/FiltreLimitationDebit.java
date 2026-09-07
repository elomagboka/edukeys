package tg.novadigital.edukeys.common.securite.limitation;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tg.novadigital.edukeys.common.securite.JournalSecurite;
import tg.novadigital.edukeys.common.securite.reseau.FiltreAdresseIpCliente;
import tg.novadigital.edukeys.common.web.CorrelationIdFilter;

/**
 * Limitation de débit sur les endpoints d'authentification (issue #58, règle
 * 5 de CLAUDE.md) : deux compteurs à attente croissante, l'un par identifiant
 * de compte soumis dans la requête, l'autre par adresse IP (voir
 * {@link LimitationDebitProperties} pour la justification des seuils).
 *
 * <p><strong>Ordre</strong> : après {@link CorrelationIdFilter} (pour que le
 * 429 porte un {@code correlationId} exploitable) et après le
 * {@code ForwardedHeaderFilter} de Boot (même raisonnement que
 * {@code CorrelationIdFilter} : {@code request.getRemoteAddr()} doit déjà
 * être la vraie IP cliente réécrite depuis {@code X-Forwarded-For}). Avant
 * {@code JwtAuthenticationFilter} et Spring Security : un compte ou une IP
 * qui dépasse son seuil ne doit atteindre ni la résolution du JWT, ni le
 * contrôleur.</p>
 *
 * <p><strong>Message d'erreur indifférencié</strong> (exigence de l'issue
 * #58, point 4) : le même message et la même forme de réponse sortent que le
 * compteur par compte ou par IP ait déclenché, qu'un compte existe ou non —
 * seul le statut HTTP (429) le distingue d'un 401 ordinaire, ce qui reste une
 * fuite d'information résiduelle assumée : on ne peut pas empêcher un
 * appelant de distinguer un 429 d'un 401 par le code de statut lui-même, mais
 * rien dans le corps ni les en-têtes ne trahit lequel des deux compteurs a
 * déclenché ni si le compte existe.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FiltreLimitationDebit extends OncePerRequestFilter {

    private static final String MESSAGE_INDIFFERENCIE =
            "Trop de tentatives. Veuillez réessayer plus tard.";

    private final LimitationDebitProperties proprietes;
    private final ObjectMapper objectMapper;
    private final CompteurAttenteCroissante compteurParCompte;
    private final CompteurAttenteCroissante compteurParIp;

    public FiltreLimitationDebit(LimitationDebitProperties proprietes, ObjectMapper objectMapper) {
        this.proprietes = proprietes;
        this.objectMapper = objectMapper;
        this.compteurParCompte = new CompteurAttenteCroissante(proprietes.getParCompte(), proprietes.getTailleMaxCache());
        this.compteurParIp = new CompteurAttenteCroissante(proprietes.getParIp(), proprietes.getTailleMaxCache());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !proprietes.getCheminsProteges().contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String adresseIp = FiltreAdresseIpCliente.adresseIpDe(request);
        RequeteAvecCorpsMisEnCache requeteMiseEnCache =
                new RequeteAvecCorpsMisEnCache(request, proprietes.getTailleMaxCorpsOctets());
        String cleCompte = extraireCleCompte(requeteMiseEnCache);

        var attenteCompte = cleCompte != null ? compteurParCompte.dureeAttenteRestante(cleCompte) : Duration.ZERO;
        var attenteIp = compteurParIp.dureeAttenteRestante(adresseIp);
        var attenteMaximale = attenteCompte.compareTo(attenteIp) >= 0 ? attenteCompte : attenteIp;

        if (attenteMaximale.compareTo(Duration.ZERO) > 0) {
            JournalSecurite.echecLimitationDebit(cleCompte != null ? JournalSecurite.empreinte(cleCompte) : "ip_seule", adresseIp);
            repondre429(request, response, attenteMaximale);
            return;
        }

        filterChain.doFilter(requeteMiseEnCache, response);

        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            // Remise a zero du SEUL compteur par compte. Reinitialiser aussi le
            // compteur par IP le rendrait annulable a volonte : un attaquant
            // disposant d'un seul compte valide - un compte parent suffit -
            // balaierait 50 comptes, se connecterait une fois avec le sien, et
            // repartirait avec un budget neuf, indefiniment. Le compteur par IP
            // ne se declencherait alors jamais, alors que le balayage est
            // precisement ce qu'il doit couvrir (issue #58). Le seuil par IP est
            // un reglage, la remise a zero etait un trou.
            if (cleCompte != null) {
                compteurParCompte.reinitialiser(cleCompte);
            }
        } else if (response.getStatus() != HttpStatus.TOO_MANY_REQUESTS.value()) {
            if (cleCompte != null) {
                compteurParCompte.enregistrerEchec(cleCompte);
            }
            compteurParIp.enregistrerEchec(adresseIp);
        }
    }

    /**
     * Réservé aux tests : repart d'un état vierge pour les deux compteurs
     * sans redémarrer le contexte Spring — indispensable puisque ce bean est
     * un singleton partagé entre toutes les méthodes de test d'un même
     * contexte ({@code AuthControllerIntegrationTest} et consorts).
     */
    public void reinitialiserPourLesTests() {
        compteurParCompte.reinitialiserTout();
        compteurParIp.reinitialiserTout();
    }

    /**
     * Réservé aux tests : nombre d'échecs actuellement retenus contre une
     * adresse IP. Permet de prouver que ce compteur n'est PAS remis à zéro par
     * une connexion réussie, sans avoir à atteindre le seuil réel (150) ni à
     * l'abaisser — l'abaisser masquerait la régression surveillée.
     */
    public int nombreDechecsParIpPourLesTests(String adresseIp) {
        return compteurParIp.nombreDechecs(adresseIp);
    }

    /**
     * Extrait l'identifiant de compte soumis (email pour {@code /login},
     * jeton de rafraîchissement pour {@code /refresh}), jamais un utilisateur
     * résolu en base : c'est la valeur telle qu'écrite par l'appelant, que le
     * compte existe ou non. {@code null} si le corps est absent, trop
     * volumineux ou syntaxiquement invalide, ou si aucun des deux champs
     * attendus n'est présent — dans ce cas, seul le compteur par IP
     * s'applique, la requête continue vers le contrôleur qui renverra 400
     * (JSON malformé) ou 401 (identifiants invalides).
     */
    private String extraireCleCompte(RequeteAvecCorpsMisEnCache requete) {
        String corps = requete.corpsCommeTexte();
        if (corps == null) {
            return null;
        }
        try {
            JsonNode racine = objectMapper.readTree(corps);
            if (racine == null) {
                return null;
            }
            JsonNode email = racine.get("email");
            if (email != null && email.isTextual() && !email.asText().isBlank()) {
                return email.asText().trim().toLowerCase();
            }
            JsonNode refreshToken = racine.get("refreshToken");
            if (refreshToken != null && refreshToken.isTextual() && !refreshToken.asText().isBlank()) {
                return "refresh:" + JournalSecurite.empreinte(refreshToken.asText());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sérialise à la main la même forme RFC 7807 que
     * {@code GestionnaireExceptionsGlobal} (type, title, status, detail,
     * correlationId) : ce filtre s'exécute hors du {@code DispatcherServlet},
     * donc avant tout {@code @RestControllerAdvice} et sans passer par les
     * {@code HttpMessageConverter} habituels — l'{@link ObjectMapper} injecté
     * ici sérialise une simple {@link Map}, jamais un
     * {@code org.springframework.http.ProblemDetail} dont la sérialisation
     * Jackson correcte dépend d'un mixin enregistré par l'infrastructure MVC,
     * absente de ce contexte.
     */
    private void repondre429(HttpServletRequest request, HttpServletResponse response, Duration attente)
            throws IOException {
        long secondesAttente = Math.max(1, attente.toSeconds() + (attente.toNanosPart() > 0 ? 1 : 0));

        Map<String, Object> corpsProblemDetail = new LinkedHashMap<>();
        corpsProblemDetail.put("type", "about:blank");
        corpsProblemDetail.put("title", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        corpsProblemDetail.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        corpsProblemDetail.put("detail", MESSAGE_INDIFFERENCIE);
        corpsProblemDetail.put("instance", request.getRequestURI());
        corpsProblemDetail.put("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUT_REQUETE));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(secondesAttente));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Octets ecrits directement, jamais getWriter() : JSON impose l'UTF-8 et
        // le message contient des accents (« reessayer »), or le writer du
        // conteneur retombe sur ISO-8859-1 faute de charset explicite et emet
        // des octets qui ne sont pas de l'UTF-8 valide — ce que MockMvc ne voit
        // pas, relisant avec le charset d'ecriture (relecture PR #74).
        // writeValueAsBytes serialise en UTF-8 par defaut. On s'abstient de
        // setCharacterEncoding : cela ajouterait « ;charset=UTF-8 » au
        // Content-Type, que GestionnaireExceptionsGlobal n'emet pas — la
        // reponse cesserait d'etre indiscernable des autres erreurs.
        response.getOutputStream().write(objectMapper.writeValueAsBytes(corpsProblemDetail));
    }
}
