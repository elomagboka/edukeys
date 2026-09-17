package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code affectations} : affectations actives uniquement (les désactivées n'apparaissent pas). */
public record MatiereDto(
        UUID id,
        String libelle,
        String code,
        boolean actif,
        Instant dateDesactivation,
        List<AffectationMatiereDto> affectations) {
}
