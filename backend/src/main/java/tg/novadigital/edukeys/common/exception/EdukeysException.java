package tg.novadigital.edukeys.common.exception;

/**
 * Racine de la hiérarchie des exceptions métier. Toute exception applicative
 * lancée par un service passe par une sous-classe de celle-ci, jamais par une
 * exception générique.
 */
public abstract class EdukeysException extends RuntimeException {

    private final CodeErreur code;

    protected EdukeysException(CodeErreur code, String message) {
        super(message);
        this.code = code;
    }

    public CodeErreur getCode() {
        return code;
    }
}
