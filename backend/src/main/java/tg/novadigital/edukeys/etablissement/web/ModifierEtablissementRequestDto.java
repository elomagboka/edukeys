package tg.novadigital.edukeys.etablissement.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tg.novadigital.edukeys.etablissement.domain.TypeEtablissement;

/** Sans {@code code} (immuable, R2) ni {@code initialiserReferentiel} (déclenché uniquement à la création). */
public record ModifierEtablissementRequestDto(
        @NotBlank @Size(max = 255) String nom,
        @Size(max = 20) String sigle,
        @NotNull TypeEtablissement typeEtablissement,
        @NotBlank @Size(max = 100) String ville,
        @Size(max = 150) String quartier,
        @Size(max = 50) String boitePostale,
        @Size(max = 255) String adresseLigne,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 30) String telephone,
        @Size(max = 255) String siteWeb,
        @NotBlank @Size(max = 64) String fuseauHoraire,
        @NotBlank @Size(max = 3) String deviseCode,
        @NotBlank @Size(max = 10) String langueDefaut) {
}
