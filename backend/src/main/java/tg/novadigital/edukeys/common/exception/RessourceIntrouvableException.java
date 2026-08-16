package tg.novadigital.edukeys.common.exception;

/** La ressource demandée n'existe pas, ou n'est pas visible depuis le contexte courant. */
public class RessourceIntrouvableException extends EdukeysException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
