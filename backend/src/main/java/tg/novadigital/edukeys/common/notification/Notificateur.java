package tg.novadigital.edukeys.common.notification;

import java.util.Map;
import java.util.UUID;

/**
 * Point d'entrée unique pour émettre une notification. Le code métier ne
 * connaît jamais le canal (in-app, email, SMS, push) ni le fournisseur : voir
 * docs/adr/0006-notifications.md. Aucune implémentation de canal en Sprint 0.
 */
public interface Notificateur {

    void envoyer(UUID destinataireId, TypeNotification type, Map<String, Object> donnees);
}
