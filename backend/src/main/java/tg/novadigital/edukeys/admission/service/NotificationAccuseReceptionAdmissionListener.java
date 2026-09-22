package tg.novadigital.edukeys.admission.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tg.novadigital.edukeys.common.notification.Notificateur;
import tg.novadigital.edukeys.common.notification.TypeNotification;

/**
 * Accusé de réception après commit (règle 12 de la spec US-06) : un échec
 * d'envoi n'annule jamais la demande, {@code Notificateur} reste le seul
 * point de contact avec les canaux (SMS sans accents, email — ADR-0006).
 */
@Component
public class NotificationAccuseReceptionAdmissionListener {

    private final Notificateur notificateur;
    private final PlafondAccusesParTelephone plafondAccusesParTelephone;

    public NotificationAccuseReceptionAdmissionListener(
            Notificateur notificateur, PlafondAccusesParTelephone plafondAccusesParTelephone) {
        this.notificateur = notificateur;
        this.plafondAccusesParTelephone = plafondAccusesParTelephone;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surSoumission(DemandeAdmissionSoumiseEvent evenement) {
        // I2 : plafond par numéro et par jour, succès compris — au-delà, la
        // demande reste enregistrée normalement, seul l'envoi est tu.
        if (!plafondAccusesParTelephone.autoriserEtCompter(evenement.responsableTelephone())) {
            return;
        }
        Map<String, Object> donnees = new HashMap<>();
        donnees.put("reference", evenement.reference());
        donnees.put("telephone", evenement.responsableTelephone());
        if (evenement.responsableEmail() != null) {
            donnees.put("email", evenement.responsableEmail());
        }
        // Aucun compte Utilisateur pour un prospect : demandeId sert d'identifiant de corrélation.
        notificateur.envoyer(evenement.demandeId(), TypeNotification.ADMISSION_ACCUSE_RECEPTION, donnees);
    }
}
