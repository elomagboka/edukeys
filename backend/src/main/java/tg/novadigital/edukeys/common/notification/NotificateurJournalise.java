package tg.novadigital.edukeys.common.notification;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implémentation par défaut de {@link Notificateur} : journalise l'intention
 * d'envoi sans émettre réellement sur aucun canal (aucun canal — in-app,
 * email, SMS, push — n'est encore implémenté, voir docs/adr/0006-notifications.md).
 * Existe pour que tout code métier appelant {@code Notificateur} dispose d'un
 * bean, sans devoir attendre le Sprint 10 pour compiler ou démarrer.
 */
@Component
public class NotificateurJournalise implements Notificateur {

    private static final Logger LOG = LoggerFactory.getLogger(NotificateurJournalise.class);

    @Override
    public void envoyer(UUID destinataireId, TypeNotification type, Map<String, Object> donnees) {
        LOG.info("notification_non_emise type={} destinataire={}", type, destinataireId);
    }
}
