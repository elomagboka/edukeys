package tg.novadigital.edukeys.identite.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank @Email String email,
        @NotBlank String motDePasse) {
}
