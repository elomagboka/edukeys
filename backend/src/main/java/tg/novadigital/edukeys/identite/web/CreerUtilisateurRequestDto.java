package tg.novadigital.edukeys.identite.web;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tg.novadigital.edukeys.identite.domain.RoleCode;

public record CreerUtilisateurRequestDto(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String nomComplet,
        @NotEmpty Set<RoleCode> roles,
        UUID siteId) {
}
