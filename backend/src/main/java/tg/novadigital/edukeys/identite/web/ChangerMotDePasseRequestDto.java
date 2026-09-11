package tg.novadigital.edukeys.identite.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangerMotDePasseRequestDto(
        @NotBlank String ancienMotDePasse,
        @NotBlank @Size(min = 8, max = 255) String nouveauMotDePasse) {
}
