package tg.novadigital.edukeys.common.exception;

/** Identifiants de connexion invalides, ou jeton de rafraîchissement inutilisable. */
public class IdentifiantsInvalidesException extends EdukeysException {

    public IdentifiantsInvalidesException(CodeErreur code, String message) {
        super(code, message);
    }
}
