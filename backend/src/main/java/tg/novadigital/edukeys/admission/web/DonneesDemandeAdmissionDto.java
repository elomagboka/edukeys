package tg.novadigital.edukeys.admission.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tg.novadigital.edukeys.admission.domain.LienResponsable;

/**
 * Champs communs d'un dossier de pré-inscription (US-06), partagés par la
 * soumission publique et la saisie back-office. Le statut n'y figure jamais
 * (règle 1 de la spec US-06) : il est imposé par le serveur.
 */
public record DonneesDemandeAdmissionDto(
        @NotNull UUID niveauId,
        UUID classeId,
        @jakarta.validation.constraints.NotBlank @Size(max = 100) String nom,
        @jakarta.validation.constraints.NotBlank @Size(max = 150) String prenoms,
        @NotNull LocalDate dateNaissance,
        @Size(max = 100) String lieuNaissance,
        @Pattern(regexp = "^[MF]$", message = "doit être M ou F") String sexe,
        @Size(min = 2, max = 2) String nationalite,
        @Size(max = 150) String etablissementOrigine,
        @jakarta.validation.constraints.NotBlank @Size(max = 100) String responsableNom,
        @jakarta.validation.constraints.NotBlank @Size(max = 150) String responsablePrenoms,
        @NotNull LienResponsable responsableLien,
        @jakarta.validation.constraints.NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "doit être au format E.164 (ex. +22890000000)")
        String responsableTelephone,
        @Email @Size(max = 254) String responsableEmail,
        boolean consentementDonnees) {
}
