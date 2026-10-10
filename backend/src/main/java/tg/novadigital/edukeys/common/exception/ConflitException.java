package tg.novadigital.edukeys.common.exception;

import java.util.Map;

/** L'opération entre en conflit avec l'état actuel de la ressource (doublon, concurrence, etc.). */
public class ConflitException extends EdukeysException {

    public ConflitException(CodeErreur code, String message) {
        super(code, message);
    }

    public ConflitException(CodeErreur code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
