package tg.novadigital.edukeys.academique.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ModifierCycleRequestDto(
        @NotBlank @Size(max = 60) String libelle,
        @Size(max = 20) String code,
        @NotNull @Positive Integer rang) {
}
