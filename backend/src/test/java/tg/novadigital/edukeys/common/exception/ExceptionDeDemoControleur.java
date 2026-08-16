package tg.novadigital.edukeys.common.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur de test exclusivement destiné à vérifier que
 * {@link GestionnaireExceptionsGlobal} traduit correctement chaque exception
 * métier. N'existe que dans les sources de test.
 */
@RestController
public class ExceptionDeDemoControleur {

    @GetMapping("/test-exceptions/{type}")
    public void lancer(@PathVariable String type) {
        switch (type) {
            case "introuvable" -> throw new RessourceIntrouvableException("Ressource introuvable.");
            case "regle-metier" -> throw new RegleMetierViolee("Règle métier violée.");
            case "conflit" -> throw new ConflitException("Conflit détecté.");
            case "acces-interdit" -> throw new AccesInterditException("Accès interdit.");
            default -> throw new IllegalStateException("Erreur inattendue.");
        }
    }
}
