package tg.novadigital.edukeys.academique.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ModifierNiveauRequestDto(
        @NotBlank @Size(max = 60) String libelle,
        @Size(max = 20) String code,
        @NotNull @Positive Integer rang,
        @NotNull UUID cycleId) {
}
