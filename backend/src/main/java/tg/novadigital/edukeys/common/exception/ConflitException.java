package tg.novadigital.edukeys.common.exception;

/** L'opération entre en conflit avec l'état actuel de la ressource (doublon, concurrence, etc.). */
public class ConflitException extends EdukeysException {

    public ConflitException(String message) {
        super(message);
    }
}
