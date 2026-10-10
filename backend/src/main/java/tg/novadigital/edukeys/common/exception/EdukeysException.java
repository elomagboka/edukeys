package tg.novadigital.edukeys.common.exception;

import java.util.Map;

/**
 * Racine de la hiérarchie des exceptions métier. Toute exception applicative
 * lancée par un service passe par une sous-classe de celle-ci, jamais par une
 * exception générique.
 */
public abstract class EdukeysException extends RuntimeException {

    private final CodeErreur code;
    private final Map<String, Object> details;

    protected EdukeysException(CodeErreur code, String message) {
        this(code, message, Map.of());
    }

    /**
     * @param details éléments structurés rendus au client dans la propriété {@code details} de la réponse
     *        (ex. les homonymes d'une inscription) ; jamais de donnée sensible
     */
    protected EdukeysException(CodeErreur code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public CodeErreur getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
