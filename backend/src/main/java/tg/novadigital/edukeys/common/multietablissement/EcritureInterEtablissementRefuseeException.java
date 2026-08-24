package tg.novadigital.edukeys.common.multietablissement;

/**
 * Levée par {@link tg.novadigital.edukeys.common.domain.RemplisseurEtablissement} (R4.4) quand une entité
 * {@code EntiteEtablissement} arrive à la persistance avec un
 * {@code etablissementId} déjà renseigné et différent de l'établissement
 * courant. Signale une tentative d'écriture inter-établissement — volontaire
 * ou accidentelle (copie d'objet entre deux contextes) — jamais une situation
 * normale côté utilisateur : ce n'est donc pas une {@code EdukeysException}
 * mappée par le {@code @RestControllerAdvice}, au même titre que
 * {@link ContexteEtablissementAbsentException}.
 */
public class EcritureInterEtablissementRefuseeException extends IllegalStateException {

    public EcritureInterEtablissementRefuseeException(String message) {
        super(message);
    }
}
