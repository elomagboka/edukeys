package tg.novadigital.edukeys.common.multietablissement;

/**
 * Levée quand un traitement exige un établissement courant
 * ({@link ContexteEtablissement#exigerEtablissementId()}) alors qu'aucun
 * contexte n'a été ouvert. Signale une erreur de programmation (traitement
 * asynchrone démarré sans {@code PorteeEtablissement}), jamais une situation
 * normale côté utilisateur — ce n'est donc pas une exception métier
 * {@code EdukeysException} mappée par le {@code @RestControllerAdvice}.
 */
public class ContexteEtablissementAbsentException extends IllegalStateException {

    public ContexteEtablissementAbsentException() {
        super("Aucun contexte d'établissement ouvert : voir ContexteEtablissement.ouvrir().");
    }

    public ContexteEtablissementAbsentException(String message) {
        super(message);
    }
}
