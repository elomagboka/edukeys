package tg.novadigital.edukeys.academique.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;

public record AnneeScolaireDto(
        UUID id,
        String libelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        StatutAnneeScolaire statut,
        Instant dateActivation,
        Instant dateCloture,
        boolean actif,
        Instant dateCreation,
        Instant dateModification) {
}
