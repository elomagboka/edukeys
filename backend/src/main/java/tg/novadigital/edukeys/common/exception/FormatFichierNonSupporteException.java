package tg.novadigital.edukeys.common.exception;

/**
 * Le fichier reçu ne correspond, par inspection des magic bytes, à aucun
 * format accepté (ex. logo d'établissement : Content-Type falsifié, ou
 * {@code image/svg+xml} volontairement refusé). Traduit en HTTP 415
 * (Unsupported Media Type) par {@link GestionnaireExceptionsGlobal}.
 */
public class FormatFichierNonSupporteException extends EdukeysException {

    public FormatFichierNonSupporteException(String message) {
        super(message);
    }
}
