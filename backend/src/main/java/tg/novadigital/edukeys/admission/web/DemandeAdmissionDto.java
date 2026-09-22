package tg.novadigital.edukeys.admission.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DemandeAdmissionDto(
        UUID id,
        String reference,
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
        List<PieceJointeAdmissionDto> pieces) {
}
