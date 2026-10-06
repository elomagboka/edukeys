package tg.novadigital.edukeys.eleve.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Résultat d'une inscription (US-08) : l'élève, son matricule, sa classe et l'accès de son compte (affiché une seule fois). */
public record InscriptionCreeeDto(
        UUID eleveId,
        UUID inscriptionId,
        String matricule,
        String nom,
        String prenoms,
        ReferenceDto classe,
        ReferenceDto niveau,
        @Schema(nullable = true, description = "Absente si la classe n'a pas de filière.")
        ReferenceDto filiere,
        ReferenceDto anneeScolaire,
        UUID siteId,
        Instant dateInscription,
        CompteEleveDto compte) {
}
