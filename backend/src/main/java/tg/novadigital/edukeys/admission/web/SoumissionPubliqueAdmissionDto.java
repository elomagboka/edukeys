package tg.novadigital.edukeys.admission.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Enveloppe de la partie JSON {@code demande} du formulaire public
 * multipart (US-06) : porte, en plus des champs métier, le champ piège
 * (honeypot, règle 6) — jamais persisté. Le jeton Turnstile (règle 4) voyage
 * désormais dans l'en-tête {@code CF-Turnstile-Response} (revue B2+I1),
 * vérifié par {@code FiltreVerificationTurnstileAdmission} avant même
 * l'analyse multipart — plus jamais dans cette enveloppe.
 */
public record SoumissionPubliqueAdmissionDto(
        @NotNull @Valid DonneesDemandeAdmissionDto demande,
        /** Champ piège : doit rester vide. Rempli par un robot naïf remplissant tous les champs du formulaire. */
        String siteWeb) {
}
