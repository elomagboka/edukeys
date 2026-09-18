package tg.novadigital.edukeys.academique.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code affectations} nullable ou vide : une matière peut exister sans affectation (critère d'acceptation). */
public record CreerMatiereRequestDto(
        @NotBlank @Size(max = 80) String libelle,
        @Size(max = 20) String code,
        @Valid List<AffectationMatiereRequestDto> affectations) {
}
