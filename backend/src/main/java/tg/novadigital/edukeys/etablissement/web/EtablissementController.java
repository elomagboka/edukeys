package tg.novadigital.edukeys.etablissement.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.common.audit.HistoriqueService;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.etablissement.mapper.EtablissementMapper;
import tg.novadigital.edukeys.etablissement.service.EtablissementService;
import tg.novadigital.edukeys.common.web.pagination.PageReponse;
import tg.novadigital.edukeys.common.web.pagination.PaginationUtils;

/**
 * Gestion des établissements clients d'Edukeys (US-00). Tranche verticale de
 * référence pour les 35 US suivantes — voir le module {@code etablissement}
 * dans son ensemble (domain/repository/service/web/mapper).
 */
@Tag(name = "Établissements")
@RestController
@RequestMapping("/api/v1/etablissements")
public class EtablissementController {

    private final EtablissementService etablissementService;
    private final EtablissementMapper etablissementMapper;
    private final HistoriqueService historiqueService;

    public EtablissementController(
            EtablissementService etablissementService,
            EtablissementMapper etablissementMapper,
            HistoriqueService historiqueService) {
        this.etablissementService = etablissementService;
        this.etablissementMapper = etablissementMapper;
        this.historiqueService = historiqueService;
    }

    @Operation(summary = "Crée un établissement (identité, coordonnées, site principal et référentiel pédagogique initialisés en une seule transaction)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Établissement créé"),
                    @ApiResponse(responseCode = "409", description = "Code ou email déjà porté par un établissement actif")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('ETABLISSEMENT_CREER')")
    public ResponseEntity<EtablissementDto> creer(@Valid @RequestBody CreerEtablissementRequestDto requete) {
        Etablissement etablissement = etablissementService.creer(requete);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(etablissement.getId())
                .toUri();
        return ResponseEntity.created(location).body(etablissementMapper.versDto(etablissement));
    }

    @Operation(summary = "Liste paginée de tous les établissements de la plateforme (opération SUPER_ADMIN)",
            responses = @ApiResponse(responseCode = "200", description = "Page d'établissements"))
    @GetMapping
    @PreAuthorize("hasAuthority('ETABLISSEMENT_CREER')")
    public PageReponse<EtablissementResumeDto> lister(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {

        Pageable pageable = PaginationUtils.construire(page, size, PaginationUtils.extraireCriteresDeTri(request));
        Page<EtablissementResumeDto> resultat = etablissementService.lister(pageable).map(etablissementMapper::versResumeDto);
        return PageReponse.depuis(resultat);
    }

    /**
     * Nouveau point d'entrée ADMIN (durcissement post-revue T-10) :
     * {@code GET /etablissements} est désormais réservé à SUPER_ADMIN, un
     * ADMIN borné à son propre établissement n'y a plus accès. Aucun
     * {@code {id}} dans le chemin — exempté de {@link GardeAccesEtablissement}
     * par construction, l'établissement résolu est celui du contexte
     * multi-établissement de l'appelant, jamais un identifiant fourni par la
     * requête.
     */
    @Operation(summary = "Établissement de l'appelant courant (ADMIN)",
            responses = @ApiResponse(responseCode = "200", description = "Établissement courant"))
    @GetMapping("/courant")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public EtablissementDto obtenirCourant() {
        return etablissementMapper.versDto(etablissementService.obtenirCourant());
    }

    @Operation(summary = "Détail d'un établissement",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Établissement trouvé"),
                    @ApiResponse(responseCode = "404", description = "Établissement introuvable")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public EtablissementDto obtenir(@PathVariable UUID id) {
        GardeAccesEtablissement.verifierAcces(id);
        return etablissementMapper.versDto(etablissementService.obtenir(id));
    }

    @Operation(summary = "Modifie l'identité et les coordonnées d'un établissement (le code est immuable)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Établissement modifié"),
                    @ApiResponse(responseCode = "404", description = "Établissement introuvable"),
                    @ApiResponse(responseCode = "409", description = "Email déjà porté par un autre établissement actif")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public EtablissementDto modifier(@PathVariable UUID id, @Valid @RequestBody ModifierEtablissementRequestDto requete) {
        GardeAccesEtablissement.verifierAcces(id);
        return etablissementMapper.versDto(etablissementService.modifier(id, requete));
    }

    @Operation(summary = "Désactive un établissement (et, en cascade logique, ses sites et son logo)",
            responses = @ApiResponse(responseCode = "204", description = "Établissement désactivé"))
    @PostMapping("/{id}/desactivation")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_CREER')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        etablissementService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Réactive un établissement, si son code et son email restent disponibles",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Établissement réactivé"),
                    @ApiResponse(responseCode = "409", description = "Code ou email repris par un autre établissement actif entre-temps")
            })
    @PostMapping("/{id}/reactivation")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_CREER')")
    public ResponseEntity<Void> reactiver(@PathVariable UUID id) {
        etablissementService.reactiver(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * R11 (voir {@link GardeAccesEtablissement}) : {@code Etablissement}
     * échappe au filtre multi-établissement (BaseEntity), donc sans ce garde
     * explicite un ADMIN pourrait lire l'historique de n'importe quel
     * établissement.
     */
    @Operation(summary = "Historique des révisions d'un établissement",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Révisions de l'établissement"),
                    @ApiResponse(responseCode = "404", description = "Établissement introuvable")
            })
    @GetMapping("/{id}/historique")
    @PreAuthorize("hasAuthority('ETABLISSEMENT_GERER')")
    public List<EtablissementHistoriqueDto> historique(@PathVariable UUID id) {
        GardeAccesEtablissement.verifierAcces(id);
        List<EtablissementHistoriqueDto> revisions = historiqueService.historique(Etablissement.class, id).stream()
                .map(etablissementMapper::versHistoriqueDto)
                .toList();
        if (revisions.isEmpty() && !etablissementService.existe(id)) {
            throw new RessourceIntrouvableException("Établissement introuvable.");
        }
        return revisions;
    }
}
