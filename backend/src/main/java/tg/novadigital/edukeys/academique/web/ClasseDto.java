package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.util.UUID;

public record ClasseDto(
        UUID id,
        String libelle,
        String suffixe,
        UUID niveauId,
        String niveauLibelle,
        UUID cycleId,
        String cycleLibelle,
        UUID filiereId,
        String filiereLibelle,
        UUID anneeScolaireId,
        String anneeScolaireLibelle,
        UUID siteId,
        Integer effectifMax,
        boolean actif,
        Instant dateCreation,
        Instant dateModification) {
}
