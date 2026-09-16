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
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.mapper.CycleMapper;
import tg.novadigital.edukeys.academique.service.CycleService;
import tg.novadigital.edukeys.common.audit.HistoriqueService;

/** Cycles du référentiel académique (US-02, D1). Pas de segment {@code {etablissementId}} : contexte issu du JWT. */
@Tag(name = "Cycles")
@RestController
@RequestMapping("/api/v1/cycles")
public class CycleController {

    private final CycleService cycleService;
    private final CycleMapper cycleMapper;
    private final HistoriqueService historiqueService;

    public CycleController(CycleService cycleService, CycleMapper cycleMapper, HistoriqueService historiqueService) {
        this.cycleService = cycleService;
        this.cycleMapper = cycleMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(operationId = "listerCycles", summary = "Liste des cycles de l'établissement courant, triée par rang",
            responses = @ApiResponse(responseCode = "200", description = "Cycles"))
    @GetMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<CycleDto> lister(@RequestParam(required = false, defaultValue = "false") boolean inclureInactifs) {
        return cycleService.lister(inclureInactifs).stream().map(cycleMapper::versDto).toList();
    }

    @Operation(operationId = "obtenirCycle", summary = "Détail d'un cycle",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Cycle trouvé"),
                    @ApiResponse(responseCode = "404", description = "Cycle introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public CycleDto obtenir(@PathVariable UUID id) {
        return cycleMapper.versDto(cycleService.obtenir(id));
    }

    @Operation(operationId = "creerCycle", summary = "Crée un cycle pour l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Cycle créé"),
                    @ApiResponse(responseCode = "409", description = "Libellé, rang ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<CycleDto> creer(@Valid @RequestBody CreerCycleRequestDto requete) {
        Cycle cycle = cycleService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(cycle.getId())
                .toUri();
        return ResponseEntity.created(location).body(cycleMapper.versDto(cycle));
    }

    @Operation(operationId = "modifierCycle", summary = "Modifie un cycle",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Cycle modifié"),
                    @ApiResponse(responseCode = "404", description = "Cycle introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé, rang ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public CycleDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierCycleRequestDto requete) {
        return cycleMapper.versDto(cycleService.modifier(id, requete));
    }

    @Operation(operationId = "desactiverCycle", summary = "Désactive un cycle sans niveau actif",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Cycle désactivé"),
                    @ApiResponse(responseCode = "404", description = "Cycle introuvable"),
                    @ApiResponse(responseCode = "422", description = "Le cycle porte encore des niveaux actifs")
            })
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        cycleService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "obtenirHistoriqueCycle", summary = "Historique des révisions d'un cycle",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions du cycle"),
                    @ApiResponse(responseCode = "404", description = "Cycle introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<CycleHistoriqueDto> historique(@PathVariable UUID id) {
        cycleService.obtenir(id);
        return historiqueService.historique(Cycle.class, id).stream().map(cycleMapper::versHistoriqueDto).toList();
    }
}
