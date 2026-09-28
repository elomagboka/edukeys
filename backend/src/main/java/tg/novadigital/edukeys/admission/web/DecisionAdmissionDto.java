package tg.novadigital.edukeys.admission.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Ligne du journal des décisions d'un dossier d'admission (US-07). */
public record DecisionAdmissionDto(
        UUID id,
        String statutPrecedent,
        String statutNouveau,
        @Schema(description = "Note interne, non transmise au parent.")
        String observation,
        UUID decideParId,
        Instant dateDecision) {
}
