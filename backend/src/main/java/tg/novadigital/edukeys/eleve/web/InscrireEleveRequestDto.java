package tg.novadigital.edukeys.eleve.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Demande d'inscription d'un élève dont le dossier d'admission a été accepté (US-08). */
public record InscrireEleveRequestDto(
        @NotNull UUID demandeAdmissionId,
        @NotNull UUID classeId,
        @Schema(description = "Version du dossier d'admission lue par le client ; une version périmée est refusée (409).")
        @NotNull Long versionDemande,
        @Schema(description = "Vrai pour confirmer qu'un élève actif de même nom, prénoms et date de naissance est bien une autre personne.",
                defaultValue = "false")
        boolean confirmerHomonyme) {
}
