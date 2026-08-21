package tg.novadigital.edukeys.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tg.novadigital.edukeys.common.web.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduit chaque exception métier en réponse RFC 7807 (ProblemDetail), avec
 * un identifiant de corrélation permettant de relier la réponse aux logs.
 */
@RestControllerAdvice
public class GestionnaireExceptionsGlobal {

    private static final Logger LOG = LoggerFactory.getLogger(GestionnaireExceptionsGlobal.class);

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ProblemDetail gererRessourceIntrouvable(RessourceIntrouvableException ex, HttpServletRequest request) {
        return construire(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    public ProblemDetail gererIdentifiantsInvalides(IdentifiantsInvalidesException ex, HttpServletRequest request) {
        return construire(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(RegleMetierViolee.class)
    public ProblemDetail gererRegleMetierViolee(RegleMetierViolee ex, HttpServletRequest request) {
        return construire(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflitException.class)
    public ProblemDetail gererConflit(ConflitException ex, HttpServletRequest request) {
        return construire(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(AccesInterditException.class)
    public ProblemDetail gererAccesInterdit(AccesInterditException ex, HttpServletRequest request) {
        return construire(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * Cible la superclasse {@code AccessDeniedException}, pas seulement
     * {@code AuthorizationDeniedException} (levée par {@code @PreAuthorize} via
     * {@code AuthorizationManagerBeforeMethodInterceptor} — correction T-04,
     * lot 1 n°3) : un futur {@code AccessDeniedException} levé directement,
     * hors méthode-sécurité, ne trouverait sinon aucun handler plus spécifique
     * que celui-ci et retomberait sur le catch-all en 500 (correction T-04,
     * relecture du catch-all). Ne masque aucune autre exception Spring
     * Security : {@code AuthenticationException} n'est jamais levée dans ce
     * code (le filtre JWT construit l'{@code Authentication} lui-même, sans
     * passer par {@code AuthenticationManager}), et les refus d'authentification
     * au niveau filtre (jeton absent ou invalide) sont interceptés par
     * {@code SecurityConfig.exceptionHandling()} avant même d'atteindre le
     * {@code DispatcherServlet} — donc avant ce {@code @RestControllerAdvice}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail gererAccesRefuse(AccessDeniedException ex, HttpServletRequest request) {
        return construire(HttpStatus.FORBIDDEN, "Accès refusé.", request);
    }

    /**
     * Corps de requête syntaxiquement invalide (champs requis manquants,
     * types incompatibles, contraintes Bean Validation). La liste des champs
     * en défaut n'inclut jamais {@code rejectedValue} : un email mal formé
     * peut être un mot de passe saisi dans le mauvais champ, et Spring
     * l'inclut par défaut dans le message de {@link FieldError} — exactement
     * ce que {@code JournalSecurite} a été créé pour éviter (correction T-04,
     * relecture du catch-all). Ni le corps de la requête ni la valeur rejetée
     * ne sont journalisés, en DEBUG comme ailleurs.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail gererValidationEchouee(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> champsInvalides = ex.getBindingResult().getFieldErrors().stream()
                .map(erreur -> erreur.getField() + " : " + erreur.getDefaultMessage())
                .toList();

        ProblemDetail problemDetail = construire(HttpStatus.BAD_REQUEST, "Requête invalide.", request);
        problemDetail.setProperty("champsInvalides", champsInvalides);
        LOG.debug("Requête invalide [correlationId={}, champs={}]",
                problemDetail.getProperties().get("correlationId"),
                ex.getBindingResult().getFieldErrors().stream().map(FieldError::getField).toList());
        return problemDetail;
    }

    /**
     * Corps de requête illisible (JSON malformé). Le contenu de la requête
     * n'est jamais journalisé ni renvoyé : il peut porter les identifiants
     * saisis par l'utilisateur.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail gererCorpsIllisible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetail problemDetail = construire(HttpStatus.BAD_REQUEST, "Corps de requête illisible.", request);
        LOG.debug("Corps de requête illisible [correlationId={}]", problemDetail.getProperties().get("correlationId"));
        return problemDetail;
    }

    /**
     * Toute exception non prévue reste un incident à investiguer : la trace
     * complète est journalisée en ERROR, avec le {@code correlationId} qui
     * relie ce log à la réponse neutre renvoyée au client. Sans ce log, le
     * correlationId de la réponse ne mène nulle part.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail gererErreurInattendue(Exception ex, HttpServletRequest request) {
        ProblemDetail problemDetail = construire(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue.", request);
        LOG.error("Erreur inattendue [correlationId={}]", problemDetail.getProperties().get("correlationId"), ex);
        return problemDetail;
    }

    private ProblemDetail construire(HttpStatus statut, String message, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(statut, message);
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUT_REQUETE);
        problemDetail.setProperty("correlationId", correlationId);
        return problemDetail;
    }
}
