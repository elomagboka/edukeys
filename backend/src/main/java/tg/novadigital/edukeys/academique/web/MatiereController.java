package tg.novadigital.edukeys.academique.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
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
import tg.novadigital.edukeys.academique.domain.AffectationMatiere;
import tg.novadigital.edukeys.academique.domain.Matiere;
import tg.novadigital.edukeys.academique.mapper.MatiereMapper;
import tg.novadigital.edukeys.academique.service.MatiereService;
import tg.novadigital.edukeys.common.audit.HistoriqueService;

/**
 * Matières du référentiel académique (US-03) et leurs affectations à un
 * niveau, pour une filière optionnelle. Pas de segment {@code {etablissementId}}.
 */
@Tag(name = "Matières")
@RestController
@RequestMapping("/api/v1/matieres")
public class MatiereController {

    private final MatiereService matiereService;
    private final MatiereMapper matiereMapper;
    private final HistoriqueService historiqueService;

    public MatiereController(MatiereService matiereService, MatiereMapper matiereMapper, HistoriqueService historiqueService) {
        this.matiereService = matiereService;
        this.matiereMapper = matiereMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(operationId = "listerMatieres",
            summary = "Liste des matières de l'établissement courant, triée par libellé",
            description = "Filtres facultatifs par niveau et/ou filière. Une matière affectée à un niveau "
                    + "sans filière (« ce niveau, toutes filières ») remonte pour toute filière de ce niveau : "
                    + "le tronc commun apparaît donc dans le filtre d'une série.",
            responses = @ApiResponse(responseCode = "200", description = "Matières"))
    @GetMapping
    @PreAuthorize("hasAuthority('MATIERE_CONSULTER')")
    public List<MatiereDto> lister(
            @RequestParam(required = false) UUID niveauId,
            @RequestParam(required = false) UUID filiereId,
            @RequestParam(required = false, defaultValue = "false") boolean inclureInactives) {
        List<Matiere> matieres = matiereService.lister(niveauId, filiereId, inclureInactives);
        Map<UUID, List<AffectationMatiere>> affectationsParMatiere = matiereService
                .affectationsActivesParMatiere(matieres.stream().map(Matiere::getId).toList());
        return matieres.stream()
                .map(matiere -> matiereMapper.versDto(matiere, affectationsParMatiere.getOrDefault(matiere.getId(), List.of())))
                .toList();
    }

    @Operation(operationId = "obtenirMatiere", summary = "Détail d'une matière et de ses affectations actives",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Matière trouvée"),
                    @ApiResponse(responseCode = "404", description = "Matière introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MATIERE_CONSULTER')")
    public MatiereDto obtenir(@PathVariable UUID id) {
        Matiere matiere = matiereService.obtenir(id);
        return matiereMapper.versDto(matiere, matiereService.affectationsActives(id));
    }

    @Operation(operationId = "creerMatiere",
            summary = "Crée une matière pour l'établissement courant, avec ses affectations initiales éventuelles",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Matière créée"),
                    @ApiResponse(responseCode = "404", description = "Niveau ou filière introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide ou affectations incohérentes")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('MATIERE_GERER')")
    public ResponseEntity<MatiereDto> creer(@Valid @RequestBody CreerMatiereRequestDto requete) {
        Matiere matiere = matiereService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(matiere.getId())
                .toUri();
        return ResponseEntity.created(location)
                .body(matiereMapper.versDto(matiere, matiereService.affectationsActives(matiere.getId())));
    }

    @Operation(operationId = "modifierMatiere", summary = "Modifie le libellé et le code d'une matière",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Matière modifiée"),
                    @ApiResponse(responseCode = "404", description = "Matière introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé ou code déjà utilisé"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MATIERE_GERER')")
    public MatiereDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierMatiereRequestDto requete) {
        Matiere matiere = matiereService.modifier(id, requete);
        return matiereMapper.versDto(matiere, matiereService.affectationsActives(id));
    }

    @Operation(operationId = "definirAffectationsMatiere",
            summary = "Remplace l'ensemble des affectations actives d'une matière par l'ensemble fourni",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Affectations définies"),
                    @ApiResponse(responseCode = "404", description = "Matière, niveau ou filière introuvable"),
                    @ApiResponse(responseCode = "409", description = "Combinaison niveau/filière déjà affectée"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide, matière inactive ou affectations incohérentes")
            })
    @PutMapping("/{id}/affectations")
    @PreAuthorize("hasAuthority('MATIERE_GERER')")
    public MatiereDto definirAffectations(@PathVariable UUID id, @Valid @RequestBody DefinirAffectationsMatiereRequestDto requete) {
        Matiere matiere = matiereService.definirAffectations(id, requete.affectations());
        return matiereMapper.versDto(matiere, matiereService.affectationsActives(id));
    }

    @Operation(operationId = "desactiverMatiere",
            summary = "Désactive une matière et ses affectations actives",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Matière désactivée"),
                    @ApiResponse(responseCode = "404", description = "Matière introuvable")
            })
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('MATIERE_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        matiereService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "obtenirHistoriqueMatiere", summary = "Historique des révisions d'une matière",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions de la matière"),
                    @ApiResponse(responseCode = "404", description = "Matière introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('MATIERE_CONSULTER')")
    public List<MatiereHistoriqueDto> historique(@PathVariable UUID id) {
        matiereService.obtenir(id);
        return historiqueService.historique(Matiere.class, id).stream().map(matiereMapper::versHistoriqueDto).toList();
    }
}
