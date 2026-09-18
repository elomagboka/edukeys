package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PeriodeAcademiqueHistoriqueDto(
        long numeroRevision,
        Instant date,
        String auteur,
        String typeRevision,
        UUID id,
        UUID anneeScolaireId,
        String libelle,
        String type,
        int ordre,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean actif) {
}
