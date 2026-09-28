package tg.novadigital.edukeys.admission.web;

import java.time.Instant;
import java.util.UUID;

/** Ligne de liste (US-06) : sans les pièces, pour éviter un N+1 sur une pagination. */
public record DemandeAdmissionResumeDto(
        UUID id,
        String reference,
        String nom,
        String prenoms,
        String niveauLibelle,
        String classeLibelle,
        String statut,
        String canal,
        Instant dateSoumission) {
}
