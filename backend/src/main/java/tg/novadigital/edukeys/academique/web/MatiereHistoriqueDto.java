package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.UUID;

public record MatiereHistoriqueDto(
        long numeroRevision,
        Instant date,
        String auteur,
        String typeRevision,
        UUID id,
        String libelle,
        String code,
        boolean actif) {
}
