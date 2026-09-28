package tg.novadigital.edukeys.admission.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tg.novadigital.edukeys.admission.domain.StatutDecisionAdmission;

/** Requête de décision sur un dossier d'admission (US-07). */
public record DecisionAdmissionRequeteDto(
        @NotNull
        StatutDecisionAdmission statut,

        @Size(max = 500)
        @Schema(description = "Note interne, non transmise au parent.")
        String observation,

        @NotNull
        Long version) {
}
