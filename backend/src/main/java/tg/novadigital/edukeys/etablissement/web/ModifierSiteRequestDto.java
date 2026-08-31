package tg.novadigital.edukeys.etablissement.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sans {@code code} : R6 traite le code de site comme immuable, à l'image du code d'établissement (R2). */
public record ModifierSiteRequestDto(
        @NotBlank @Size(max = 255) String nom,
        @Size(max = 100) String ville,
        @Size(max = 150) String quartier,
        @Size(max = 255) String adresseLigne,
        @Size(max = 30) String telephone) {
}
