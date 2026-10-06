package tg.novadigital.edukeys.eleve.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.eleve.mapper.InscriptionMapper;
import tg.novadigital.edukeys.eleve.service.InscriptionService;

/**
 * Inscription d'un élève accepté (US-08) : crée l'élève, son matricule, son inscription en classe et le
 * compte avec son mot de passe temporaire. Aucune logique métier ici (CLAUDE.md, règle 8).
 */
@Tag(name = "Inscriptions")
@RestController
@RequestMapping("/api/v1/inscriptions")
public class InscriptionController {

    private final InscriptionService inscriptionService;
    private final InscriptionMapper inscriptionMapper;

    public InscriptionController(InscriptionService inscriptionService, InscriptionMapper inscriptionMapper) {
        this.inscriptionService = inscriptionService;
        this.inscriptionMapper = inscriptionMapper;
    }

    @Operation(operationId = "inscrireEleve",
            summary = "Inscrit un élève dont le dossier d'admission est accepté (matricule, compte, classe)",
            description = "Le mot de passe temporaire du compte n'est rendu qu'une fois, dans cette réponse (Cache-Control: no-store). "
                    + "Il n'est jamais envoyé par SMS ni par notification.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Élève inscrit"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "401", description = "Non authentifié"),
                    @ApiResponse(responseCode = "403", description = "Accès refusé"),
                    @ApiResponse(responseCode = "404", description = "Dossier ou classe introuvable"),
                    @ApiResponse(responseCode = "409", description = "Dossier déjà inscrit, version périmée, homonyme à confirmer, identifiant déjà pris ou ligne du compteur de matricule absente (MODIFICATION_CONCURRENTE, à réessayer)"),
                    @ApiResponse(responseCode = "422", description = "Dossier non accepté, classe inactive ou complète, année ou niveau incohérents, année clôturée, matricule épuisé, expiration hors bornes")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('INSCRIPTION_CREER')")
    public ResponseEntity<InscriptionCreeeDto> inscrire(@Valid @RequestBody InscrireEleveRequestDto requete) {
        InscriptionCreeeDto dto = inscriptionMapper.versDto(inscriptionService.inscrire(inscriptionMapper.versCommande(requete)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(dto);
    }
}
