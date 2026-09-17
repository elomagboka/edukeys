package tg.novadigital.edukeys.academique.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code libelle} nullable (D5) : composé par le service depuis
 * {@code niveau.libelle + " " + suffixe} si absent ou blanc.
 * {@code anneeScolaireId} nullable : par défaut l'année active de
 * l'établissement. {@code siteId} nullable : par défaut le site principal
 * (D3) — jamais fait confiance directement, toujours validé via
 * {@code SiteQuery} côté service (R11).
 */
public record CreerClasseRequestDto(
        @Size(max = 60) String libelle,
        @Size(max = 10) String suffixe,
        @NotNull UUID niveauId,
        UUID filiereId,
        UUID anneeScolaireId,
        UUID siteId,
        @Positive Integer effectifMax) {
}
