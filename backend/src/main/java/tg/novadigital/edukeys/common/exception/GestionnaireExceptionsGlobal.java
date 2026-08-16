package tg.novadigital.edukeys.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ProblemDetail gererRessourceIntrouvable(RessourceIntrouvableException ex, HttpServletRequest request) {
        return construire(HttpStatus.NOT_FOUND, ex.getMessage(), request);
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

    @ExceptionHandler(Exception.class)
    public ProblemDetail gererErreurInattendue(Exception ex, HttpServletRequest request) {
        return construire(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue.", request);
    }

    private ProblemDetail construire(HttpStatus statut, String message, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(statut, message);
        Object correlationId = request.getAttribute(CorrelationIdFilter.ATTRIBUT_REQUETE);
        problemDetail.setProperty("correlationId", correlationId);
        return problemDetail;
    }
}
