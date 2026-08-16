package tg.novadigital.edukeys.common.exception;

/** L'utilisateur authentifié n'a pas le droit d'effectuer cette opération. */
public class AccesInterditException extends EdukeysException {

    public AccesInterditException(String message) {
        super(message);
    }
}
