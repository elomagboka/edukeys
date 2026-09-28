package tg.novadigital.edukeys.admission.web;

/**
 * Accusé de réception public (règle « soumission » de la spec US-06, revue
 * I4 puis 3e revue point 1) : jamais d'identifiant interne, jamais de statut
 * ni de date, et jamais la référence séquentielle {@code PRE-<annee>-<seq>}
 * — un attaquant capable de comparer deux codes de suivi ne doit rien pouvoir
 * en déduire sur la position du compteur ni sur l'ordre des soumissions.
 * {@code codeSuivi} est opaque (SecureRandom 128 bits) — la réponse est
 * strictement identique que le dossier soit nouveau ou déjà existant
 * (idempotence).
 */
public record AccuseReceptionAdmissionDto(String codeSuivi, String message) {
}
