package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.UUID;

public record FiliereDto(
        UUID id,
        String libelle,
        String code,
        UUID cycleId,
        String cycleLibelle,
        boolean actif,
        Instant dateCreation,
        Instant dateModification) {
}
