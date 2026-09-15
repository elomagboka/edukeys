package tg.novadigital.edukeys.academique.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code libelle} absent (DELTA 1, US-01) : conserve la valeur courante,
 * plutôt que de régénérer un libellé par défaut qui écraserait
 * silencieusement une saisie personnalisée.
 */
public record ModifierAnneeScolaireRequestDto(
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        @Size(max = 20) String libelle) {
}
