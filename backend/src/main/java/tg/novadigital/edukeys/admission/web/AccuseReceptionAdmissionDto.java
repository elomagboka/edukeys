package tg.novadigital.edukeys.admission.web;

/**
 * Accusé de réception public (règle « soumission » de la spec US-06, revue
 * I4) : jamais d'identifiant interne, jamais de statut ni de date — la
 * réponse est strictement identique que le dossier soit nouveau ou déjà
 * existant (idempotence), pour ne rien laisser fuiter sur l'état interne du
 * dossier à un appelant non authentifié.
 */
public record AccuseReceptionAdmissionDto(String reference, String message) {
}
