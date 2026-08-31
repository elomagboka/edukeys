package tg.novadigital.edukeys.common.exception;

/**
 * Le fichier reçu dépasse la taille maximale autorisée (ex. logo
 * d'établissement plafonné à 1 Mo). Traduit en HTTP 413 (Payload Too Large)
 * par {@link GestionnaireExceptionsGlobal}.
 */
public class FichierTropVolumineuxException extends EdukeysException {

    public FichierTropVolumineuxException(String message) {
        super(message);
    }
}
