package tg.novadigital.edukeys.admission.service;

import java.util.UUID;

/**
 * Publié après la soumission d'une demande d'admission (US-06), consommé
 * après commit ({@code @TransactionalEventListener}) pour déclencher l'accusé
 * de réception via {@code Notificateur} — un échec d'envoi n'annule jamais la
 * demande. Jamais publié sur une réponse idempotente d'un dossier déjà
 * notifié (règle 12 de la spec US-06).
 */
public record DemandeAdmissionSoumiseEvent(
        UUID demandeId,
        String reference,
        String responsableTelephone,
        String responsableEmail) {
}
