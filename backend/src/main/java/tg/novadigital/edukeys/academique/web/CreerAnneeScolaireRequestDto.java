package tg.novadigital.edukeys.academique.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code libelle} volontairement sans {@code @NotBlank} ni {@code @Pattern}
 * (DELTA 1, US-01) : son absence est légale, le service en génère un par
 * défaut au format {@code AAAA-AAAA}.
 */
public record CreerAnneeScolaireRequestDto(
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        @Size(max = 20) String libelle) {
}
