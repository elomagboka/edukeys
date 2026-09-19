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
import tg.novadigital.edukeys.academique.domain.PeriodeAcademique;
import tg.novadigital.edukeys.academique.mapper.PeriodeAcademiqueMapper;
import tg.novadigital.edukeys.academique.service.PeriodeAcademiqueService;
import tg.novadigital.edukeys.common.audit.HistoriqueService;

/**
 * Périodes académiques (trimestres/semestres) d'une année scolaire (US-05).
 * Pas de segment {@code {etablissementId}}.
 */
@Tag(name = "Périodes académiques")
@RestController
@RequestMapping("/api/v1/periodes-academiques")
public class PeriodeAcademiqueController {

    private final PeriodeAcademiqueService periodeAcademiqueService;
    private final PeriodeAcademiqueMapper periodeAcademiqueMapper;
    private final HistoriqueService historiqueService;

    public PeriodeAcademiqueController(PeriodeAcademiqueService periodeAcademiqueService,
                                        PeriodeAcademiqueMapper periodeAcademiqueMapper,
                                        HistoriqueService historiqueService) {
        this.periodeAcademiqueService = periodeAcademiqueService;
        this.periodeAcademiqueMapper = periodeAcademiqueMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(operationId = "listerPeriodesAcademiques",
            summary = "Liste des périodes académiques de l'établissement courant, triée par ordre",
            description = "Filtre facultatif par année scolaire.",
            responses = @ApiResponse(responseCode = "200", description = "Périodes académiques"))
    @GetMapping
    @PreAuthorize("hasAuthority('PERIODE_CONSULTER')")
    public List<PeriodeAcademiqueDto> lister(
            @RequestParam(required = false) UUID anneeScolaireId,
            @RequestParam(required = false, defaultValue = "false") boolean inclureInactives) {
        List<PeriodeAcademique> periodes = periodeAcademiqueService.lister(anneeScolaireId, inclureInactives);
        Map<UUID, String> libelles = periodeAcademiqueService
                .libellesAnneesScolaires(periodes.stream().map(PeriodeAcademique::getAnneeScolaireId).toList());
        return periodes.stream()
                .map(periode -> periodeAcademiqueMapper.versDto(periode, libelles.get(periode.getAnneeScolaireId()),
                        periodeAcademiqueService.estEnCours(periode)))
                .toList();
    }

    @Operation(operationId = "obtenirPeriodeAcademiqueEnCours",
            summary = "Période académique en cours de l'année scolaire active de l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Période en cours"),
                    @ApiResponse(responseCode = "404", description = "Aucune période en cours")
            })
    @GetMapping("/en-cours")
    @PreAuthorize("hasAuthority('PERIODE_CONSULTER')")
    public PeriodeAcademiqueDto obtenirEnCours() {
        PeriodeAcademique periode = periodeAcademiqueService.obtenirEnCours();
        Map<UUID, String> libelles = periodeAcademiqueService.libellesAnneesScolaires(List.of(periode.getAnneeScolaireId()));
        return periodeAcademiqueMapper.versDto(periode, libelles.get(periode.getAnneeScolaireId()), true);
    }

    @Operation(operationId = "obtenirPeriodeAcademique", summary = "Détail d'une période académique",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Période académique trouvée"),
                    @ApiResponse(responseCode = "404", description = "Période académique introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERIODE_CONSULTER')")
    public PeriodeAcademiqueDto obtenir(@PathVariable UUID id) {
        PeriodeAcademique periode = periodeAcademiqueService.obtenir(id);
        Map<UUID, String> libelles = periodeAcademiqueService.libellesAnneesScolaires(List.of(periode.getAnneeScolaireId()));
        return periodeAcademiqueMapper.versDto(periode, libelles.get(periode.getAnneeScolaireId()),
                periodeAcademiqueService.estEnCours(periode));
    }

    @Operation(operationId = "creerPeriodeAcademique",
            summary = "Crée une période académique pour une année scolaire de l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Période académique créée"),
                    @ApiResponse(responseCode = "404", description = "Année scolaire introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé, ordre déjà utilisé ou période chevauchante"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide, dates hors bornes ou année clôturée")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('PERIODE_GERER')")
    public ResponseEntity<PeriodeAcademiqueDto> creer(@Valid @RequestBody CreerPeriodeAcademiqueRequestDto requete) {
        PeriodeAcademique periode = periodeAcademiqueService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(periode.getId())
                .toUri();
        Map<UUID, String> libelles = periodeAcademiqueService.libellesAnneesScolaires(List.of(periode.getAnneeScolaireId()));
        return ResponseEntity.created(location)
                .body(periodeAcademiqueMapper.versDto(periode, libelles.get(periode.getAnneeScolaireId()),
                        periodeAcademiqueService.estEnCours(periode)));
    }

    @Operation(operationId = "modifierPeriodeAcademique",
            summary = "Modifie le libellé, l'ordre et les dates d'une période académique",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Période académique modifiée"),
                    @ApiResponse(responseCode = "404", description = "Période académique introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé, ordre déjà utilisé ou période chevauchante"),
                    @ApiResponse(responseCode = "422", description = "Requête invalide, dates hors bornes ou année clôturée")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERIODE_GERER')")
    public PeriodeAcademiqueDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierPeriodeAcademiqueRequestDto requete) {
        PeriodeAcademique periode = periodeAcademiqueService.modifier(id, requete);
        Map<UUID, String> libelles = periodeAcademiqueService.libellesAnneesScolaires(List.of(periode.getAnneeScolaireId()));
        return periodeAcademiqueMapper.versDto(periode, libelles.get(periode.getAnneeScolaireId()),
                periodeAcademiqueService.estEnCours(periode));
    }

    @Operation(operationId = "desactiverPeriodeAcademique", summary = "Désactive une période académique",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Période académique désactivée"),
                    @ApiResponse(responseCode = "404", description = "Période académique introuvable"),
                    @ApiResponse(responseCode = "422", description = "Désactivation refusée : année scolaire clôturée")
            })
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('PERIODE_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        periodeAcademiqueService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "obtenirHistoriquePeriodeAcademique", summary = "Historique des révisions d'une période académique",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions de la période académique"),
                    @ApiResponse(responseCode = "404", description = "Période académique introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('PERIODE_CONSULTER')")
    public List<PeriodeAcademiqueHistoriqueDto> historique(@PathVariable UUID id) {
        periodeAcademiqueService.obtenir(id);
        return historiqueService.historique(PeriodeAcademique.class, id).stream()
                .map(periodeAcademiqueMapper::versHistoriqueDto)
                .toList();
    }
}
