package tg.novadigital.edukeys.admission.service;

import java.util.UUID;

import tg.novadigital.edukeys.admission.domain.StatutAdmission;

/**
 * Publié après qu'une décision a été prise sur un dossier d'admission
 * (US-07), consommé après commit pour déclencher la notification au
 * responsable via {@code Notificateur} — un échec d'envoi n'annule jamais la
 * décision. Ne porte jamais l'observation (note interne, US-07).
 */
public record DecisionAdmissionPriseEvent(
        UUID demandeId,
        StatutAdmission statutNouveau,
        String codeSuivi,
        String responsableTelephone,
        String responsableEmail) {
}
