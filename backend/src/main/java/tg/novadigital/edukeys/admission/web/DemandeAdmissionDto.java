package tg.novadigital.edukeys.admission.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record DemandeAdmissionDto(
        UUID id,
        String reference,
        String codeSuivi,
        UUID anneeScolaireId,
        UUID niveauId,
        UUID classeId,
        String nom,
        String prenoms,
        LocalDate dateNaissance,
        String lieuNaissance,
        String sexe,
        String nationalite,
        String etablissementOrigine,
        String responsableNom,
        String responsablePrenoms,
        String responsableLien,
        String responsableTelephone,
        String responsableEmail,
        String statut,
        String canal,
        Instant dateSoumission,
        long version,
        Instant dateDecision,
        @Schema(description = "Note interne, non transmise au parent.")
        String observationDecision,
        UUID decideParId,
        List<PieceJointeAdmissionDto> pieces,
        List<DecisionAdmissionDto> decisions) {
}
