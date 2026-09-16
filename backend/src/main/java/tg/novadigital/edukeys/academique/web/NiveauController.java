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
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.mapper.NiveauMapper;
import tg.novadigital.edukeys.academique.service.NiveauService;
import tg.novadigital.edukeys.common.audit.HistoriqueService;

/** Niveaux du référentiel académique (US-02), rattachés à un cycle. Pas de segment {@code {etablissementId}}. */
@Tag(name = "Niveaux")
@RestController
@RequestMapping("/api/v1/niveaux")
public class NiveauController {

    private final NiveauService niveauService;
    private final NiveauMapper niveauMapper;
    private final HistoriqueService historiqueService;

    public NiveauController(NiveauService niveauService, NiveauMapper niveauMapper, HistoriqueService historiqueService) {
        this.niveauService = niveauService;
        this.niveauMapper = niveauMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(operationId = "listerNiveaux", summary = "Liste des niveaux de l'établissement courant, triée par cycle puis rang",
            responses = @ApiResponse(responseCode = "200", description = "Niveaux"))
    @GetMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<NiveauDto> lister(
            @RequestParam(required = false) UUID cycleId,
            @RequestParam(required = false, defaultValue = "false") boolean inclureInactifs) {
        return niveauService.lister(cycleId, inclureInactifs).stream().map(niveauMapper::versDto).toList();
    }

    @Operation(operationId = "obtenirNiveau", summary = "Détail d'un niveau",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Niveau trouvé"),
                    @ApiResponse(responseCode = "404", description = "Niveau introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public NiveauDto obtenir(@PathVariable UUID id) {
        return niveauMapper.versDto(niveauService.obtenir(id));
    }

    @Operation(operationId = "creerNiveau", summary = "Crée un niveau rattaché à un cycle de l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Niveau créé"),
                    @ApiResponse(responseCode = "404", description = "Cycle introuvable ou inactif"),
                    @ApiResponse(responseCode = "409", description = "Libellé, rang ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<NiveauDto> creer(@Valid @RequestBody CreerNiveauRequestDto requete) {
        Niveau niveau = niveauService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(niveau.getId())
                .toUri();
        return ResponseEntity.created(location).body(niveauMapper.versDto(niveau));
    }

    @Operation(operationId = "modifierNiveau", summary = "Modifie un niveau",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Niveau modifié"),
                    @ApiResponse(responseCode = "404", description = "Niveau ou cycle introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé, rang ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public NiveauDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierNiveauRequestDto requete) {
        return niveauMapper.versDto(niveauService.modifier(id, requete));
    }

    @Operation(operationId = "desactiverNiveau", summary = "Désactive un niveau sans classe active",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Niveau désactivé"),
                    @ApiResponse(responseCode = "404", description = "Niveau introuvable"),
                    @ApiResponse(responseCode = "422", description = "Le niveau porte encore des classes actives")
            })
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        niveauService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "obtenirHistoriqueNiveau", summary = "Historique des révisions d'un niveau",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions du niveau"),
                    @ApiResponse(responseCode = "404", description = "Niveau introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<NiveauHistoriqueDto> historique(@PathVariable UUID id) {
        niveauService.obtenir(id);
        return historiqueService.historique(Niveau.class, id).stream().map(niveauMapper::versHistoriqueDto).toList();
    }
}
