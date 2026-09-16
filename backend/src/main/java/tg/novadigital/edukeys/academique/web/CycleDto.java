package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.UUID;

public record CycleDto(
        UUID id,
        String libelle,
        String code,
        int rang,
        boolean actif,
        Instant dateCreation,
        Instant dateModification) {
}
