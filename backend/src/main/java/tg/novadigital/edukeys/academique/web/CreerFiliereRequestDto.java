package tg.novadigital.edukeys.academique.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code cycleId} nullable (D4) : le rattachement au cycle est optionnel. */
public record CreerFiliereRequestDto(
        @NotBlank @Size(max = 80) String libelle,
        @Size(max = 20) String code,
        UUID cycleId) {
}
