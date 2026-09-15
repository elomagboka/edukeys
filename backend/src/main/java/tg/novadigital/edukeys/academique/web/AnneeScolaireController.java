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
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.mapper.AnneeScolaireMapper;
import tg.novadigital.edukeys.academique.service.AnneeScolaireService;
import tg.novadigital.edukeys.common.audit.HistoriqueService;

/**
 * Années scolaires (US-01). Pas de segment {@code {etablissementId}} : le
 * contexte vient du JWT via {@code ContexteEtablissementFilter}, contrairement
 * à {@code SiteController} qui sert aussi {@code SUPER_ADMIN}. Liste non
 * paginée assumée : 5 à 15 années par établissement sur la durée de vie du
 * produit.
 */
@Tag(name = "Années scolaires")
@RestController
@RequestMapping("/api/v1/annees-scolaires")
public class AnneeScolaireController {

    private final AnneeScolaireService anneeScolaireService;
    private final AnneeScolaireMapper anneeScolaireMapper;
    private final HistoriqueService historiqueService;

    public AnneeScolaireController(
            AnneeScolaireService anneeScolaireService,
            AnneeScolaireMapper anneeScolaireMapper,
            HistoriqueService historiqueService) {
        this.anneeScolaireService = anneeScolaireService;
        this.anneeScolaireMapper = anneeScolaireMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(operationId = "listerAnneesScolaires", summary = "Liste des années scolaires de l'établissement courant",
            responses = @ApiResponse(responseCode = "200", description = "Années scolaires"))
    @GetMapping
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_CONSULTER')")
    public List<AnneeScolaireDto> lister(
            @RequestParam(required = false) StatutAnneeScolaire statut,
            @RequestParam(required = false, defaultValue = "false") boolean inclureInactives) {
        return anneeScolaireService.lister(statut, inclureInactives).stream()
                .map(anneeScolaireMapper::versDto)
                .toList();
    }

    @Operation(operationId = "obtenirAnneeScolaireActive", summary = "Année scolaire actuellement active de l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Année active"),
                    @ApiResponse(responseCode = "404", description = "Aucune année active pour cet établissement")
            })
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_CONSULTER')")
    public AnneeScolaireDto obtenirActive() {
        return anneeScolaireMapper.versDto(anneeScolaireService.obtenirActive());
    }

    @Operation(operationId = "obtenirAnneeScolaire", summary = "Détail d'une année scolaire",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Année scolaire trouvée"),
                    @ApiResponse(responseCode = "404", description = "Année scolaire introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_CONSULTER')")
    public AnneeScolaireDto obtenir(@PathVariable UUID id) {
        return anneeScolaireMapper.versDto(anneeScolaireService.obtenir(id));
    }

    @Operation(operationId = "creerAnneeScolaire", summary = "Crée une année scolaire pour l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Année scolaire créée"),
                    @ApiResponse(responseCode = "409", description = "Libellé déjà utilisé ou période chevauchant une autre année"),
                    @ApiResponse(responseCode = "422", description = "Dates incohérentes, durée invalide ou libellé vide")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_GERER')")
    public ResponseEntity<AnneeScolaireDto> creer(@Valid @RequestBody CreerAnneeScolaireRequestDto requete) {
        AnneeScolaire anneeScolaire = anneeScolaireService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(anneeScolaire.getId())
                .toUri();
        return ResponseEntity.created(location).body(anneeScolaireMapper.versDto(anneeScolaire));
    }

    @Operation(operationId = "modifierAnneeScolaire", summary = "Modifie les dates (et éventuellement le libellé) d'une année scolaire",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Année scolaire modifiée"),
                    @ApiResponse(responseCode = "404", description = "Année scolaire introuvable"),
                    @ApiResponse(responseCode = "409", description = "Libellé déjà utilisé ou période chevauchant une autre année"),
                    @ApiResponse(responseCode = "422", description = "Dates incohérentes, durée invalide ou année clôturée")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_GERER')")
    public AnneeScolaireDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierAnneeScolaireRequestDto requete) {
        return anneeScolaireMapper.versDto(anneeScolaireService.modifier(id, requete));
    }

    @Operation(operationId = "activerAnneeScolaire",
            summary = "Active une année en préparation (bascule transactionnelle : l'année active en cours est clôturée)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Année scolaire activée"),
                    @ApiResponse(responseCode = "404", description = "Année scolaire introuvable"),
                    @ApiResponse(responseCode = "422", description = "Transition invalide")
            })
    @PostMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_GERER')")
    public AnneeScolaireDto activer(@PathVariable UUID id) {
        return anneeScolaireMapper.versDto(anneeScolaireService.activer(id));
    }

    @Operation(operationId = "cloturerAnneeScolaire", summary = "Clôture une année active",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Année scolaire clôturée"),
                    @ApiResponse(responseCode = "404", description = "Année scolaire introuvable"),
                    @ApiResponse(responseCode = "422", description = "Transition invalide")
            })
    @PostMapping("/{id}/cloture")
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_GERER')")
    public AnneeScolaireDto cloturer(@PathVariable UUID id) {
        return anneeScolaireMapper.versDto(anneeScolaireService.cloturer(id));
    }

    @Operation(operationId = "desactiverAnneeScolaire", summary = "Désactive une année en préparation",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Année scolaire désactivée"),
                    @ApiResponse(responseCode = "404", description = "Année scolaire introuvable"),
                    @ApiResponse(responseCode = "422", description = "Désactivation refusée hors préparation")
            })
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        anneeScolaireService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "obtenirHistoriqueAnneeScolaire", summary = "Historique des révisions d'une année scolaire",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions de l'année scolaire"),
                    @ApiResponse(responseCode = "404", description = "Année scolaire introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('ANNEE_SCOLAIRE_CONSULTER')")
    public List<AnneeScolaireHistoriqueDto> historique(@PathVariable UUID id) {
        // Vérifie l'existence dans l'établissement courant avant l'historique
        // (filtre Hibernate déjà appliqué par ce findById), même logique que
        // EtablissementController#historique.
        anneeScolaireService.obtenir(id);
        return historiqueService.historique(AnneeScolaire.class, id).stream()
                .map(anneeScolaireMapper::versHistoriqueDto)
                .toList();
    }
}
