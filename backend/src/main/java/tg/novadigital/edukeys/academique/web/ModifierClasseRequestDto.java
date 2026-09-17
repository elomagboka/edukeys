package tg.novadigital.edukeys.academique.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Pas de {@code anneeScolaireId} (R12/R8) : une classe ne change jamais d'année après création. */
public record ModifierClasseRequestDto(
        @Size(max = 60) String libelle,
        @Size(max = 10) String suffixe,
        @NotNull UUID niveauId,
        UUID filiereId,
        UUID siteId,
        @Positive Integer effectifMax) {
}
