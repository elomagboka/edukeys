package tg.novadigital.edukeys.academique.web;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Une affectation cible d'une matière à un niveau (US-03). {@code filiereId}
 * nullable : {@code null} signifie « ce niveau, toutes filières ». La clé
 * fonctionnelle (niveauId, filiereId) ne doit pas être dupliquée au sein
 * d'une même requête ({@code definirAffectations}), contrôlé par le service.
 */
public record AffectationMatiereRequestDto(
        @NotNull UUID niveauId,
        UUID filiereId,
        @Positive BigDecimal coefficient,
        @Positive BigDecimal volumeHoraire,
        Boolean obligatoire) {
}
