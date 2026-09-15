package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AnneeScolaireHistoriqueDto(
        long numeroRevision,
        Instant date,
        String auteur,
        String typeRevision,
        UUID id,
        String libelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        String statut,
        boolean actif) {
}
