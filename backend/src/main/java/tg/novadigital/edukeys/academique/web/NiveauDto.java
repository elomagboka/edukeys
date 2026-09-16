package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.UUID;

/** {@code cycleLibelle} aplati (spec §7) : un écran = un appel, pas de DTO imbriqué. */
public record NiveauDto(
        UUID id,
        String libelle,
        String code,
        int rang,
        UUID cycleId,
        String cycleLibelle,
        boolean actif,
        Instant dateCreation,
        Instant dateModification) {
}
