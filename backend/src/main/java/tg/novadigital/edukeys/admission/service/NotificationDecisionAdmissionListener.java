package tg.novadigital.edukeys.admission.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.common.notification.Notificateur;
import tg.novadigital.edukeys.common.notification.TypeNotification;

/**
 * Notifie le responsable d'une décision prise sur un dossier d'admission
 * (US-07), après commit ({@code @TransactionalEventListener}) : un échec
 * d'envoi n'annule jamais la décision, et une exception ici ne doit jamais
 * remonter après le commit (try/catch + log, même patron que
 * {@link NotificationAccuseReceptionAdmissionListener}).
 *
 * <p><b>Livraison non garantie</b> : sans mécanisme d'outbox, un échec après
 * commit (ex. panne du fournisseur SMS/email au moment précis de l'envoi)
 * n'est ni rejoué ni compensé — risque accepté, suivi dans l'issue #100.</p>
 */
@Component
public class NotificationDecisionAdmissionListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDecisionAdmissionListener.class);

    private final Notificateur notificateur;

    public NotificationDecisionAdmissionListener(Notificateur notificateur) {
        this.notificateur = notificateur;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surDecision(DecisionAdmissionPriseEvent evenement) {
        try {
            TypeNotification type = resoudreType(evenement.statutNouveau());
            Map<String, Object> donnees = new HashMap<>();
            donnees.put("codeSuivi", evenement.codeSuivi());
            donnees.put("telephone", evenement.responsableTelephone());
            if (evenement.responsableEmail() != null) {
                donnees.put("email", evenement.responsableEmail());
            }
            donnees.put("statut", evenement.statutNouveau().name());
            // Aucun compte Utilisateur pour un prospect : demandeId sert d'identifiant de corrélation.
            // Jamais l'observation (note interne, US-07) : absente de donnees par construction.
            notificateur.envoyer(evenement.demandeId(), type, donnees);
        } catch (RuntimeException e) {
            LOG.error("Échec de la notification de décision d'admission [demandeId={}]", evenement.demandeId(), e);
        }
    }

    private static TypeNotification resoudreType(StatutAdmission statut) {
        return switch (statut) {
            case ACCEPTEE -> TypeNotification.ADMISSION_DECISION_ACCEPTEE;
            case REFUSEE -> TypeNotification.ADMISSION_DECISION_REFUSEE;
            case LISTE_ATTENTE -> TypeNotification.ADMISSION_DECISION_LISTE_ATTENTE;
            default -> throw new IllegalStateException("Statut de décision inattendu : " + statut);
        };
    }
}
