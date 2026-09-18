package tg.novadigital.edukeys.academique.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Ensemble cible des affectations d'une matière (US-03, R3) : remplace
 * intégralement l'ensemble actif actuel (diff appliqué côté service). Liste
 * vide autorisée et signifie « désactiver toutes les affectations actives »
 * — {@code @NotNull} sur la liste elle-même, pas sur son contenu.
 */
public record DefinirAffectationsMatiereRequestDto(
        @NotNull @Valid List<AffectationMatiereRequestDto> affectations) {
}
