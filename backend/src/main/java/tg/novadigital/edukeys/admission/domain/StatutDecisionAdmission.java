package tg.novadigital.edukeys.admission.domain;

/**
 * Sous-ensemble de {@link StatutAdmission} constituant les seules cibles
 * valides d'une décision prise sur un dossier (US-07) : EN_ATTENTE n'est
 * jamais une cible de transition, ANNULEE reste hors de portée d'une décision
 * (autre geste métier). Enum dédié plutôt que réutiliser {@link StatutAdmission}
 * tel quel : le formulaire de décision ne doit jamais proposer une cible
 * inatteignable.
 */
public enum StatutDecisionAdmission {
    ACCEPTEE,
    REFUSEE,
    LISTE_ATTENTE;

    public StatutAdmission versStatutAdmission() {
        return StatutAdmission.valueOf(this.name());
    }
}
