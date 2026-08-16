package tg.novadigital.edukeys.common.notification;

/**
 * Types de notification métier. Chaque type porte le drapeau d'éligibilité au
 * canal SMS — payant à l'unité, réservé aux notifications à forte valeur
 * (voir docs/adr/0006-notifications.md).
 */
public enum TypeNotification {

    ABSENCE_NON_JUSTIFIEE(true),
    ECHEANCE_PAIEMENT(true),
    CONVOCATION(true),
    FERMETURE_EXCEPTIONNELLE(true),
    NOUVELLE_NOTE(false),
    DEVOIR_PUBLIE(false),
    MESSAGE_MESSAGERIE(false);

    private final boolean eligibleSms;

    TypeNotification(boolean eligibleSms) {
        this.eligibleSms = eligibleSms;
    }

    public boolean isEligibleSms() {
        return eligibleSms;
    }
}
