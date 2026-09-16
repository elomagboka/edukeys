package tg.novadigital.edukeys.academique.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.mapper.FiliereMapper;
import tg.novadigital.edukeys.academique.service.FiliereService;
import tg.novadigital.edukeys.common.audit.HistoriqueService;

/** Filières du référentiel académique (US-02, D4). Pas de segment {@code {etablissementId}}. */
@Tag(name = "Filières")
@RestController
@RequestMapping("/api/v1/filieres")
public class FiliereController {

    private final FiliereService filiereService;
    private final FiliereMapper filiereMapper;
    private final HistoriqueService historiqueService;

    public FiliereController(FiliereService filiereService, FiliereMapper filiereMapper, HistoriqueService historiqueService) {
        this.filiereService = filiereService;
        this.filiereMapper = filiereMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(operationId = "listerFilieres", summary = "Liste des filières de l'établissement courant, triée par libellé",
            responses = @ApiResponse(responseCode = "200", description = "Filières"))
    @GetMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<FiliereDto> lister(
            @RequestParam(required = false) UUID cycleId,
            @RequestParam(required = false, defaultValue = "false") boolean inclureInactives) {
        return filiereService.lister(cycleId, inclureInactives).stream().map(filiereMapper::versDto).toList();
    }

    @Operation(operationId = "obtenirFiliere", summary = "Détail d'une filière",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Filière trouvée"),
                    @ApiResponse(responseCode = "404", description = "Filière introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public FiliereDto obtenir(@PathVariable UUID id) {
        return filiereMapper.versDto(filiereService.obtenir(id));
    }

    @Operation(operationId = "creerFiliere", summary = "Crée une filière pour l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Filière créée"),
                    @ApiResponse(responseCode = "404", description = "Cycle introuvable ou inactif"),
                    @ApiResponse(responseCode = "409", description = "Libellé ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<FiliereDto> creer(@Valid @RequestBody CreerFiliereRequestDto requete) {
        Filiere filiere = filiereService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(filiere.getId())
                .toUri();
        return ResponseEntity.created(location).body(filiereMapper.versDto(filiere));
    }

    @Operation(operationId = "modifierFiliere", summary = "Modifie une filière",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Filière modifiée"),
                    @ApiResponse(responseCode = "404", description = "Filière ou cycle introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public FiliereDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierFiliereRequestDto requete) {
        return filiereMapper.versDto(filiereService.modifier(id, requete));
    }

    @Operation(operationId = "desactiverFiliere", summary = "Désactive une filière non référencée par des classes actives",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Filière désactivée"),
                    @ApiResponse(responseCode = "404", description = "Filière introuvable"),
                    @ApiResponse(responseCode = "422", description = "La filière est encore référencée par des classes actives")
            })
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        filiereService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "obtenirHistoriqueFiliere", summary = "Historique des révisions d'une filière",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions de la filière"),
                    @ApiResponse(responseCode = "404", description = "Filière introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<FiliereHistoriqueDto> historique(@PathVariable UUID id) {
        filiereService.obtenir(id);
        return historiqueService.historique(Filiere.class, id).stream().map(filiereMapper::versHistoriqueDto).toList();
    }
}
