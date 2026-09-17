package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.UUID;

public record NiveauHistoriqueDto(
        long numeroRevision,
        Instant date,
        String auteur,
        String typeRevision,
        UUID id,
        String libelle,
        String code,
        int rang,
        UUID cycleId,
        boolean actif) {
}
