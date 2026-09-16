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
import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.academique.mapper.ClasseMapper;
import tg.novadigital.edukeys.academique.service.ClasseService;
import tg.novadigital.edukeys.common.audit.HistoriqueService;

/**
 * Classes d'une année scolaire (US-02, D2). Pas de segment
 * {@code {etablissementId}}. {@code anneeScolaireId} absent sur la liste
 * résout vers l'année active de l'établissement courant ; s'il n'y en a pas,
 * liste vide (spec §6).
 */
@Tag(name = "Classes")
@RestController
@RequestMapping("/api/v1/classes")
public class ClasseController {

    private final ClasseService classeService;
    private final ClasseMapper classeMapper;
    private final HistoriqueService historiqueService;

    public ClasseController(ClasseService classeService, ClasseMapper classeMapper, HistoriqueService historiqueService) {
        this.classeService = classeService;
        this.classeMapper = classeMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(operationId = "listerClasses",
            summary = "Liste des classes de l'établissement courant, triée par rang de niveau puis libellé",
            responses = @ApiResponse(responseCode = "200", description = "Classes"))
    @GetMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<ClasseDto> lister(
            @RequestParam(required = false) UUID anneeScolaireId,
            @RequestParam(required = false) UUID niveauId,
            @RequestParam(required = false) UUID filiereId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false, defaultValue = "false") boolean inclureInactives) {
        return classeService.lister(anneeScolaireId, niveauId, filiereId, siteId, inclureInactives).stream()
                .map(classeMapper::versDto)
                .toList();
    }

    @Operation(operationId = "obtenirClasse", summary = "Détail d'une classe",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Classe trouvée"),
                    @ApiResponse(responseCode = "404", description = "Classe introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public ClasseDto obtenir(@PathVariable UUID id) {
        return classeMapper.versDto(classeService.obtenir(id));
    }

    @Operation(operationId = "creerClasse", summary = "Crée une classe pour l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Classe créée"),
                    @ApiResponse(responseCode = "404", description = "Niveau, filière ou année scolaire introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé déjà utilisé pour cette année scolaire"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide, référentiel inactif, site invalide ou année clôturée")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<ClasseDto> creer(@Valid @RequestBody CreerClasseRequestDto requete) {
        Classe classe = classeService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(classe.getId())
                .toUri();
        return ResponseEntity.created(location).body(classeMapper.versDto(classe));
    }

    @Operation(operationId = "modifierClasse", summary = "Modifie une classe (jamais son année scolaire)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Classe modifiée"),
                    @ApiResponse(responseCode = "404", description = "Classe, niveau ou filière introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé déjà utilisé pour cette année scolaire"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide, référentiel inactif, site invalide ou année clôturée")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ClasseDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierClasseRequestDto requete) {
        return classeMapper.versDto(classeService.modifier(id, requete));
    }

    @Operation(operationId = "desactiverClasse", summary = "Désactive une classe dont l'année n'est pas clôturée",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Classe désactivée"),
                    @ApiResponse(responseCode = "404", description = "Classe introuvable"),
                    @ApiResponse(responseCode = "422", description = "Année scolaire clôturée")
            })
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        classeService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "obtenirHistoriqueClasse", summary = "Historique des révisions d'une classe",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions de la classe"),
                    @ApiResponse(responseCode = "404", description = "Classe introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('STRUCTURE_ACADEMIQUE_CONSULTER')")
    public List<ClasseHistoriqueDto> historique(@PathVariable UUID id) {
        classeService.obtenir(id);
        return historiqueService.historique(Classe.class, id).stream().map(classeMapper::versHistoriqueDto).toList();
    }
}
