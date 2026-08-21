package tg.novadigital.edukeys.common.exception;

/** Identifiants de connexion invalides, ou jeton de rafraîchissement inutilisable. */
public class IdentifiantsInvalidesException extends EdukeysException {

    public IdentifiantsInvalidesException(String message) {
        super(message);
    }
}
