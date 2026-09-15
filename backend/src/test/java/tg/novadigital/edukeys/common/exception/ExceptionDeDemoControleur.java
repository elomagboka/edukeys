package tg.novadigital.edukeys.common.exception;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Contrôleur de test exclusivement destiné à vérifier que
 * {@link GestionnaireExceptionsGlobal} traduit correctement chaque exception
 * métier. N'existe que dans les sources de test.
 */
@RestController
public class ExceptionDeDemoControleur {

    @Operation(operationId = "lancerExceptionDeDemo")
    @GetMapping("/test-exceptions/{type}")
    public void lancer(@PathVariable String type) {
        switch (type) {
            case "introuvable" -> throw new RessourceIntrouvableException(CodeErreur.ERREUR_INATTENDUE, "Ressource introuvable.");
            case "regle-metier" -> throw new RegleMetierViolee(CodeErreur.ERREUR_INATTENDUE, "Règle métier violée.");
            case "conflit" -> throw new ConflitException(CodeErreur.ERREUR_INATTENDUE, "Conflit détecté.");
            case "acces-interdit" -> throw new AccesInterditException(CodeErreur.ERREUR_INATTENDUE, "Accès interdit.");
            case "autorisation-refusee" -> throw new AuthorizationDeniedException("Autorisation refusée.");
            default -> throw new IllegalStateException("Erreur inattendue.");
        }
    }

    /**
     * Corps validé par Bean Validation : sert à déclencher
     * {@code MethodArgumentNotValidException} avec une valeur arbitraire dans
     * le champ {@code email}, exactement comme {@code LoginRequestDto}.
     */
    @Operation(operationId = "validerRequeteDeDemo")
    @PostMapping("/test-exceptions/valider")
    public void valider(@Valid @RequestBody RequeteDeDemo requete) {
    }

    public record RequeteDeDemo(@NotBlank @Email String email) {
    }
}
