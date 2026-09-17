package tg.novadigital.edukeys.academique.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModifierMatiereRequestDto(
        @NotBlank @Size(max = 80) String libelle,
        @Size(max = 20) String code) {
}
