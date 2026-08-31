package tg.novadigital.edukeys.etablissement.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreerSiteRequestDto(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String nom,
        @Size(max = 100) String ville,
        @Size(max = 150) String quartier,
        @Size(max = 255) String adresseLigne,
        @Size(max = 30) String telephone) {
}
