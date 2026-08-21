package tg.novadigital.edukeys.identite.web;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import tg.novadigital.edukeys.common.web.pagination.PageReponse;
import tg.novadigital.edukeys.common.web.pagination.PaginationUtils;
import tg.novadigital.edukeys.identite.security.UtilisateurPrincipal;
import tg.novadigital.edukeys.identite.service.UtilisateurService;

/**
 * Consultation des comptes utilisateurs. Endpoint de référence prouvant le
 * critère de fin de T-04 : {@code /moi} n'exige qu'une authentification
 * valide (tout rôle passe), la liste complète est réservée à la permission
 * {@code UTILISATEUR_GERER_PLATEFORME} — portée par SUPER_ADMIN seul.
 * {@code ADMIN} porte {@code UTILISATEUR_GERER} (gestion des comptes de SON
 * établissement, ADR-0002 §5) mais pas la variante plateforme : les deux
 * permissions ont été distinguées après qu'une relecture ait montré qu'une
 * permission partagée entre les deux rôles laissait ADMIN lire les comptes de
 * tous les établissements. Tout autre rôle authentifié reçoit un 403 — voir
 * AuthControllerIntegrationTest pour la preuve bout-en-bout.
 */
@Tag(name = "Utilisateurs")
@RestController
@RequestMapping("/api/v1/utilisateurs")
public class UtilisateurController {

    /**
     * Liste blanche des champs triables : {@code motDePasseHache} est une
     * propriété comme une autre pour Spring Data, donc trier dessus sans
     * filtrage exposerait l'ordre lexicographique des empreintes bcrypt
     * (relecture T-04, repasse n°2). Un champ hors liste est ignoré plutôt
     * que provoquer un 500 (l'inexistant lèverait une PropertyReferenceException).
     */
    private static final Set<String> CHAMPS_TRIABLES = Set.of("email", "nomComplet", "dateCreation", "actif");

    private final UtilisateurService utilisateurService;

    public UtilisateurController(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    @Operation(summary = "Profil du compte authentifié",
            responses = @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur courant"))
    @GetMapping("/moi")
    @PreAuthorize("isAuthenticated()")
    public UtilisateurResumeDto moi(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return UtilisateurResumeDto.depuis(utilisateurService.obtenir(principal.utilisateurId()));
    }

    /**
     * Tous les établissements confondus, délibérément : voir la javadoc de
     * {@link UtilisateurService#listerTous(Pageable)}. Réservé à
     * {@code UTILISATEUR_GERER_PLATEFORME}, jamais à {@code UTILISATEUR_GERER}
     * (qui, lui, est borné à l'établissement d'ADMIN — voir la javadoc de
     * classe pour l'incident que cette distinction corrige).
     */
    @Operation(summary = "Liste paginée des comptes utilisateurs de tous les établissements (administration de plateforme)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page de comptes"),
                    @ApiResponse(responseCode = "403", description = "Permission UTILISATEUR_GERER_PLATEFORME requise")
            })
    @GetMapping
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER_PLATEFORME')")
    public PageReponse<UtilisateurResumeDto> lister(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {

        List<String> criteresDeTriAutorises = PaginationUtils.extraireCriteresDeTri(request).stream()
                .filter(critere -> CHAMPS_TRIABLES.contains(critere.split(",")[0].trim()))
                .toList();
        // Sans tri explicite, PostgreSQL ne garantit aucun ordre stable entre
        // deux pages (un compte pourrait apparaître deux fois ou jamais) :
        // dateCreation par défaut, un UUID v7 étant lui-même ordonné dans le
        // temps (relecture T-04, repasse n°2).
        if (criteresDeTriAutorises.isEmpty()) {
            criteresDeTriAutorises = List.of("dateCreation,asc");
        }

        Pageable pageable = PaginationUtils.construire(page, size, criteresDeTriAutorises);
        Page<UtilisateurResumeDto> resultat = utilisateurService.listerTous(pageable).map(UtilisateurResumeDto::depuis);
        return PageReponse.depuis(resultat);
    }
}
