package tg.novadigital.edukeys.academique.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tg.novadigital.edukeys.academique.domain.TypePeriode;

public record CreerPeriodeAcademiqueRequestDto(
        @NotNull UUID anneeScolaireId,
        @NotBlank @Size(max = 40) String libelle,
        @NotNull TypePeriode type,
        @Min(1) @Max(6) int ordre,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin) {
}
