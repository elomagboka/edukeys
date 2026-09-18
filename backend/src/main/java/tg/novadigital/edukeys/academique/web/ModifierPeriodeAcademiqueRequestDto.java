package tg.novadigital.edukeys.academique.web;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ni {@code type} ni {@code anneeScolaireId} : immuables après création (R9). */
public record ModifierPeriodeAcademiqueRequestDto(
        @NotBlank @Size(max = 40) String libelle,
        @Min(1) @Max(6) int ordre,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin) {
}
