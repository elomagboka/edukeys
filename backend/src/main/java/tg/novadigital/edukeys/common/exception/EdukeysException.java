package tg.novadigital.edukeys.common.exception;

/**
 * Racine de la hiérarchie des exceptions métier. Toute exception applicative
 * lancée par un service passe par une sous-classe de celle-ci, jamais par une
 * exception générique.
 */
public abstract class EdukeysException extends RuntimeException {

    protected EdukeysException(String message) {
        super(message);
    }
}
