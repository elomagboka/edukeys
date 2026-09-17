package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.UUID;

public record ClasseHistoriqueDto(
        long numeroRevision,
        Instant date,
        String auteur,
        String typeRevision,
        UUID id,
        String libelle,
        String suffixe,
        UUID niveauId,
        UUID filiereId,
        UUID anneeScolaireId,
        UUID siteId,
        Integer effectifMax,
        boolean actif) {
}
