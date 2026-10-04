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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.securite.IdentifiantConnexion;
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

    private static final AntPathMatcher CHEMIN_MATCHER = new AntPathMatcher();

    private final LimitationDebitProperties proprietes;
    private final ObjectMapper objectMapper;
    private final CompteurAttenteCroissante compteurParCompte;
    private final CompteurAttenteCroissante compteurParIp;
    private final CompteurAttenteCroissante compteurParIpAdmission;
    /** 3e revue, point 3 : budget de soumissions RÉUSSIES par IP et par jour — {@link #compteurParIpAdmission} ne compte que les échecs. */
    private final CompteurBudgetJournalier compteurBudgetSuccesAdmission;

    public FiltreLimitationDebit(LimitationDebitProperties proprietes, ObjectMapper objectMapper) {
        this.proprietes = proprietes;
        this.objectMapper = objectMapper;
        this.compteurParCompte = new CompteurAttenteCroissante(proprietes.getParCompte(), proprietes.getTailleMaxCache());
        this.compteurParIp = new CompteurAttenteCroissante(proprietes.getParIp(), proprietes.getTailleMaxCache());
        this.compteurParIpAdmission = new CompteurAttenteCroissante(proprietes.getParIpAdmission(), proprietes.getTailleMaxCache());
        this.compteurBudgetSuccesAdmission = new CompteurBudgetJournalier(
                proprietes.getBudgetSuccesAdmissionParJour(), Duration.ofDays(1), proprietes.getTailleMaxCache());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return proprietes.getCheminsProteges().stream().noneMatch(motif -> CHEMIN_MATCHER.match(motif, uri))
                && proprietes.getCheminsAdmission().stream().noneMatch(motif -> CHEMIN_MATCHER.match(motif, uri));
    }

    /** {@code true} si l'URI appartient à la famille admission publique (US-06, I3) : compteur IP séparé, plus généreux, jamais de lecture de corps. */
    private boolean estCheminAdmission(String uri) {
        return proprietes.getCheminsAdmission().stream().anyMatch(motif -> CHEMIN_MATCHER.match(motif, uri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String adresseIp = FiltreAdresseIpCliente.adresseIpDe(request);

        // I3 : famille "admission publique" — jamais de lecture du corps (multipart ou
        // non), un compteur IP dédié et volontairement plus généreux (B1 : le flux d'une
        // requête multipart ne doit JAMAIS être consommé par ce filtre, Tomcat doit rester
        // seul à l'analyser pour que getParts()/getParameter() fonctionnent en aval).
        if (estCheminAdmission(request.getRequestURI())) {
            doFiltrerAdmission(request, response, filterChain, adresseIp);
            return;
        }

        // Charset déclaré non Unicode (ex. ISO-8859-1) : le convertisseur Spring décoderait le corps avec ce charset
        // alors que ce filtre lit les octets avec la détection Unicode de Jackson — les deux pourraient diverger sur
        // l'identifiant. Refusé sur les chemins limités plutôt que d'aligner deux décodeurs (415).
        // On lit l'en-tête Content-Type lui-même (comme le convertisseur Spring), pas getCharacterEncoding() :
        // le CharacterEncodingFilter de Boot force l'encodage de la requête à UTF-8 en amont et le masquerait.
        if (charsetDeclareNonUnicode(request.getContentType())) {
            repondre415(request, response);
            return;
        }

        RequeteAvecCorpsMisEnCache requeteMiseEnCache = new RequeteAvecCorpsMisEnCache(request, proprietes.getTailleMaxCorpsOctets());
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
     * Second rideau, famille "admission publique" (I3) : uniquement le
     * compteur IP dédié {@link #compteurParIpAdmission}, jamais de lecture du
     * corps — ni JSON ni multipart. C'est ce qui garantit à Tomcat un flux
     * d'entrée intact pour analyser le multipart (B1) et permet au filtre
     * Turnstile placé avant celui-ci de refuser sans qu'aucun octet du corps
     * n'ait été consommé.
     *
     * <p><strong>Asynchrone depuis la 3e revue (point 4)</strong> : la route
     * de soumission renvoie désormais un {@code DeferredResult}, traité en
     * deux passages de ce filtre ({@link #shouldNotFilterAsyncDispatch()}
     * retourne {@code false}) — les vérifications préalables (attente, budget)
     * ne s'exécutent que sur le dispatch {@code REQUEST} initial, jamais sur
     * le redispatch {@code ASYNC}, sans quoi elles s'appliqueraient deux fois.
     * La comptabilisation (échec/succès) est reportée au redispatch
     * {@code ASYNC} pour une route encore en cours de traitement : avant cela,
     * {@code response.getStatus()} ne porte pas encore le statut final.</p>
     */
    private void doFiltrerAdmission(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain, String adresseIp)
            throws ServletException, IOException {
        boolean estRedispatchAsync = jakarta.servlet.DispatcherType.ASYNC.equals(request.getDispatcherType());
        boolean estSoumission = "POST".equalsIgnoreCase(request.getMethod());

        if (!estRedispatchAsync) {
            var attenteIp = compteurParIpAdmission.dureeAttenteRestante(adresseIp);
            if (attenteIp.compareTo(Duration.ZERO) > 0) {
                JournalSecurite.echecLimitationDebit("ip_seule", adresseIp);
                repondre429(request, response, attenteIp);
                return;
            }

            // 3e revue, point 3 : budget de soumissions réussies, vérifié AVANT
            // le traitement (pour refuser la N+1e tentative) — uniquement sur
            // la route de dépôt (POST), jamais sur la simple consultation de
            // l'offre (GET), pure lecture sans coût d'écriture.
            if (estSoumission && !compteurBudgetSuccesAdmission.budgetDisponible(adresseIp)) {
                JournalSecurite.echecLimitationDebit("ip_seule", adresseIp);
                repondre429(request, response, Duration.ofDays(1));
                return;
            }
        }

        filterChain.doFilter(request, response);

        if (request.isAsyncStarted()) {
            // Traitement encore en cours (attente du plancher de temps de
            // réponse, point 4) : le statut final n'est pas encore connu, la
            // comptabilisation se fera au redispatch ASYNC.
            return;
        }

        boolean succes = response.getStatus() >= 200 && response.getStatus() < 300;
        if (response.getStatus() != HttpStatus.TOO_MANY_REQUESTS.value() && !succes) {
            compteurParIpAdmission.enregistrerEchec(adresseIp);
        }
        if (estSoumission && succes) {
            compteurBudgetSuccesAdmission.enregistrerSucces(adresseIp);
        }
    }

    /** Indispensable pour que ce filtre soit ré-invoqué au redispatch ASYNC de la soumission publique (point 4, 3e revue). */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * Réservé aux tests : repart d'un état vierge pour les trois compteurs
     * sans redémarrer le contexte Spring — indispensable puisque ce bean est
     * un singleton partagé entre toutes les méthodes de test d'un même
     * contexte ({@code AuthControllerIntegrationTest} et consorts).
     */
    public void reinitialiserPourLesTests() {
        compteurParCompte.reinitialiserTout();
        compteurParIp.reinitialiserTout();
        compteurParIpAdmission.reinitialiserTout();
        compteurBudgetSuccesAdmission.reinitialiserTout();
    }

    /** Réservé aux tests : nombre de soumissions réussies déjà comptées contre cette IP (point 3, 3e revue). */
    public int nombreDeSuccesAdmissionPourLesTests(String adresseIp) {
        return compteurBudgetSuccesAdmission.nombreDeSucces(adresseIp);
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
     * Extrait l'identifiant de compte soumis (identifiant de connexion, email ou matricule, pour {@code /login},
     * jeton de rafraîchissement pour {@code /refresh}), jamais un utilisateur
     * résolu en base : c'est la valeur telle qu'écrite par l'appelant, que le
     * compte existe ou non. {@code null} si le corps est absent, trop
     * volumineux ou syntaxiquement invalide, ou si aucun des deux champs
     * attendus n'est présent — dans ce cas, seul le compteur par IP
     * s'applique, la requête continue vers le contrôleur qui renverra 400
     * (JSON malformé) ou 401 (identifiants invalides).
     */
    private String extraireCleCompte(RequeteAvecCorpsMisEnCache requete) {
        byte[] corps = requete.corpsOctets();
        if (corps == null) {
            return null;
        }
        try {
            // Octets bruts, même détection d'encodage (UTF-8/16/32) que le convertisseur du contrôleur ; même
            // ObjectMapper (donc mêmes options de parseur, ex. détection stricte des champs dupliqués).
            JsonNode racine = objectMapper.readTree(corps);
            if (racine == null) {
                return null;
            }
            // Seul un identifiant textuel est une clé de compte : un nombre ou un booléen JSON est
            // rejeté en 400 par LoginRequestDto (désérialiseur strict), il n'authentifie jamais,
            // donc n'a pas besoin de compteur par compte (le compteur par IP s'applique).
            //
            // Alias de compatibilité : l'ancien champ "email" reste accepté une version (API et site
            // statique se déploient séparément, ADR-0004/0007). MÊME règle que LoginRequestDto :
            // "identifiant" prime dès qu'il est présent et non nul (même non textuel : requête
            // rejetée en 400, jamais retombée sur "email") ; "email" n'est lu que s'il est absent.
            // À supprimer avec l'alias du DTO (issue #107, retrait de l'alias "email").
            JsonNode identifiant = racine.get("identifiant");
            if (identifiant == null || identifiant.isNull()) {
                identifiant = racine.get("email");
            }
            if (identifiant != null && identifiant.isTextual() && !identifiant.asText().isBlank()) {
                return IdentifiantConnexion.normaliser(identifiant.asText());
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

    private static boolean charsetDeclareNonUnicode(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            java.nio.charset.Charset charset = MediaType.parseMediaType(contentType).getCharset();
            return charset != null && !charset.name().toUpperCase(java.util.Locale.ROOT).startsWith("UTF-");
        } catch (RuntimeException e) {
            // Content-Type illisible ou charset inconnu : le convertisseur Spring le refusera lui aussi (400/415).
            return true;
        }
    }

    private void repondre415(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> corpsProblemDetail = new LinkedHashMap<>();
        corpsProblemDetail.put("type", "about:blank");
        corpsProblemDetail.put("title", HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase());
        corpsProblemDetail.put("status", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        corpsProblemDetail.put("detail", "Encodage de contenu non supporté : utilisez UTF-8.");
        corpsProblemDetail.put("instance", request.getRequestURI());
        corpsProblemDetail.put("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUT_REQUETE));
        corpsProblemDetail.put("code", CodeErreur.ENCODAGE_NON_SUPPORTE.name());
        response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(corpsProblemDetail));
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
        corpsProblemDetail.put("code", CodeErreur.TROP_DE_REQUETES.name());

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
