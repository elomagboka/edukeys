package tg.novadigital.edukeys.etablissement.web;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.etablissement.domain.Site;
import tg.novadigital.edukeys.etablissement.mapper.SiteMapper;
import tg.novadigital.edukeys.etablissement.service.SiteService;

/** Sites (annexes) d'un établissement (US-00, docs/adr/0005-sites-et-annexes.md). Liste non paginée : 1 à 5 sites typiquement. */
@Tag(name = "Sites")
@RestController
@RequestMapping("/api/v1/etablissements/{etablissementId}/sites")
public class SiteController {

    private final SiteService siteService;
    private final SiteMapper siteMapper;

    public SiteController(SiteService siteService, SiteMapper siteMapper) {
        this.siteService = siteService;
        this.siteMapper = siteMapper;
    }

    @Operation(summary = "Liste des sites actifs d'un établissement",
            responses = @ApiResponse(responseCode = "200", description = "Sites de l'établissement"))
    @GetMapping
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public List<SiteDto> lister(@PathVariable UUID etablissementId) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        return siteService.lister(etablissementId).stream().map(siteMapper::versDto).toList();
    }

    @Operation(summary = "Crée un site pour un établissement",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Site créé"),
                    @ApiResponse(responseCode = "409", description = "Code de site déjà utilisé dans cet établissement")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public ResponseEntity<SiteDto> creer(@PathVariable UUID etablissementId, @Valid @RequestBody CreerSiteRequestDto requete) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        Site site = siteService.creer(etablissementId, requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(site.getId())
                .toUri();
        return ResponseEntity.created(location).body(siteMapper.versDto(site));
    }

    @Operation(summary = "Modifie un site",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Site modifié"),
                    @ApiResponse(responseCode = "404", description = "Site introuvable")
            })
    @PutMapping("/{siteId}")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public SiteDto modifier(@PathVariable UUID etablissementId, @PathVariable UUID siteId, @Valid @RequestBody ModifierSiteRequestDto requete) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        return siteMapper.versDto(siteService.modifier(etablissementId, siteId, requete));
    }

    @Operation(summary = "Désigne un site comme principal (bascule transactionnelle)",
            responses = @ApiResponse(responseCode = "204", description = "Site désigné principal"))
    @PostMapping("/{siteId}/principal")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public ResponseEntity<Void> designerPrincipal(@PathVariable UUID etablissementId, @PathVariable UUID siteId) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        siteService.designerPrincipal(etablissementId, siteId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Désactive un site (refusé pour le site principal)",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Site désactivé"),
                    @ApiResponse(responseCode = "422", description = "Le site principal ne peut pas être désactivé directement")
            })
    @PostMapping("/{siteId}/desactivation")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID etablissementId, @PathVariable UUID siteId) {
        GardeAccesEtablissement.verifierAcces(etablissementId);
        siteService.desactiver(etablissementId, siteId);
        return ResponseEntity.noContent().build();
    }
}
