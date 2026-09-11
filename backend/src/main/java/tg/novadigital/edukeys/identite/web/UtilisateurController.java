package tg.novadigital.edukeys.identite.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.common.web.pagination.PageReponse;
import tg.novadigital.edukeys.common.web.pagination.PaginationUtils;
import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;
import tg.novadigital.edukeys.identite.mapper.UtilisateurMapper;
import tg.novadigital.edukeys.identite.security.UtilisateurPrincipal;
import tg.novadigital.edukeys.identite.service.UtilisateurService;

/**
 * Gestion des comptes utilisateurs et des rôles RBAC (US-04). Endpoint de
 * référence prouvant le critère de fin de T-04 : {@code /moi} n'exige qu'une
 * authentification valide (tout rôle passe), la liste complète (tous
 * établissements) est réservée à {@code UTILISATEUR_GERER_PLATEFORME} —
 * portée par SUPER_ADMIN seul. Tous les nouveaux endpoints de ce contrôleur
 * sont bornés à l'établissement courant (résolu depuis le JWT, jamais depuis
 * un identifiant de requête) et gardés par une permission explicite
 * ({@code UTILISATEUR_GERER}, {@code UTILISATEUR_CONSULTER} ou
 * {@code ROLE_ATTRIBUER} — CLAUDE.md, règle 11).
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
    private final UtilisateurMapper utilisateurMapper;

    public UtilisateurController(UtilisateurService utilisateurService, UtilisateurMapper utilisateurMapper) {
        this.utilisateurService = utilisateurService;
        this.utilisateurMapper = utilisateurMapper;
    }

    @Operation(summary = "Profil du compte authentifié",
            responses = @ApiResponse(responseCode = "200", description = "Profil de l'utilisateur courant"))
    @GetMapping("/moi")
    @PreAuthorize("isAuthenticated()")
    public UtilisateurResumeDto moi(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return UtilisateurResumeDto.depuis(utilisateurService.obtenirSoiMeme(principal));
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

    /**
     * Exige {@code ROLE_ATTRIBUER} en plus de {@code UTILISATEUR_GERER}
     * (revue post-implémentation, point 3) : {@code CreerUtilisateurRequestDto.roles}
     * est {@code @NotEmpty}, donc toute création attribue déjà au moins un
     * rôle — un porteur de {@code UTILISATEUR_GERER} seul pourrait sinon
     * créer un compte {@code ADMIN} et lire son mot de passe temporaire,
     * s'octroyant en deux appels exactement ce que la scission de
     * {@link tg.novadigital.edukeys.identite.domain.Permission#ROLE_ATTRIBUER}
     * visait à empêcher. Les rôles demandés à la création n'étant jamais
     * vides, les deux permissions sont exigées inconditionnellement ici,
     * plutôt que conditionnées à un ensemble de rôles jugés « privilégiés »
     * (surface d'erreur plus simple à auditer).
     */
    /**
     * Ne fait plus qu'une chose : créer un compte (revue post-implémentation,
     * 3e passe — le rattachement d'une affectation à un compte déjà existant
     * a été supprimé, voir la Javadoc de {@code UtilisateurService#creerCompteAvecRoles}).
     * Toujours {@code 201}, toujours un mot de passe temporaire dans la
     * réponse.
     */
    @Operation(summary = "Crée un compte dans l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Compte créé, avec le mot de passe temporaire de son titulaire (retourné une seule fois)"),
                    @ApiResponse(responseCode = "409", description = "Cet email est déjà utilisé sur la plateforme (message unique, y compris pour un compte de plateforme)"),
                    @ApiResponse(responseCode = "422", description = "Aucun rôle fourni, ou SUPER_ADMIN demandé (rôle de plateforme, non attribuable ici)")
            })
    @PostMapping
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER') and hasAuthority('ROLE_ATTRIBUER')")
    public ResponseEntity<CompteCreeDto> creer(@Valid @RequestBody CreerUtilisateurRequestDto requete) {
        UtilisateurService.CompteCree compteCree = utilisateurService.creerCompteAvecRoles(
                requete.email(), requete.nomComplet(), requete.roles(), requete.siteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(utilisateurMapper.versCompteCreeDto(compteCree));
    }

    @Operation(summary = "Liste paginée des comptes de l'établissement courant",
            responses = @ApiResponse(responseCode = "200", description = "Page de comptes de l'établissement courant"))
    @GetMapping("/mon-etablissement")
    @PreAuthorize("hasAuthority('UTILISATEUR_CONSULTER')")
    public PageReponse<UtilisateurCompteDto> listerMonEtablissement(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = PaginationUtils.construire(page, size, List.of());
        Page<AffectationEtablissement> resultat = utilisateurService.listerAffectationsEtablissementCourant(pageable);
        return PageReponse.depuis(resultat.map(utilisateurMapper::versCompteDto));
    }

    @Operation(summary = "Compte de l'établissement courant par identifiant",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Compte de l'établissement courant"),
                    @ApiResponse(responseCode = "404", description = "Aucun compte actif portant cet identifiant dans l'établissement courant")
            })
    @GetMapping("/mon-etablissement/{id}")
    @PreAuthorize("hasAuthority('UTILISATEUR_CONSULTER')")
    public UtilisateurCompteDto obtenirMonEtablissement(@PathVariable UUID id) {
        AffectationEtablissement affectation = utilisateurService.obtenirAffectationDansEtablissementCourant(id);
        return utilisateurMapper.versCompteDto(affectation);
    }

    @Operation(summary = "Remplace les rôles d'un compte de l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Rôles remplacés"),
                    @ApiResponse(responseCode = "404", description = "Aucun compte actif portant cet identifiant dans l'établissement courant"),
                    @ApiResponse(responseCode = "422", description = "Aucun rôle fourni, SUPER_ADMIN demandé, ou tentative de modifier ses propres rôles")
            })
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_ATTRIBUER')")
    public ResponseEntity<Void> remplacerRoles(
            @PathVariable UUID id,
            @Valid @RequestBody ModifierRolesRequestDto requete,
            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        utilisateurService.remplacerRoles(id, requete.roles(), principal.utilisateurId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Désactive (logiquement) l'affectation d'un compte à l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Affectation désactivée"),
                    @ApiResponse(responseCode = "404", description = "Aucun compte actif portant cet identifiant dans l'établissement courant"),
                    @ApiResponse(responseCode = "422", description = "Tentative de désactiver son propre compte, ou dernier ADMIN actif de l'établissement")
            })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
    public ResponseEntity<Void> desactiver(
            @PathVariable UUID id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        utilisateurService.desactiverDansEtablissementCourant(id, principal.utilisateurId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Réactive l'affectation d'un compte à l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Affectation réactivée"),
                    @ApiResponse(responseCode = "404", description = "Aucun compte portant cet identifiant dans l'établissement courant")
            })
    @PostMapping("/{id}/reactiver")
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
    public ResponseEntity<Void> reactiver(@PathVariable UUID id) {
        utilisateurService.reactiverDansEtablissementCourant(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Régénère un mot de passe temporaire pour un compte de l'établissement courant",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Nouveau mot de passe temporaire (retourné une seule fois)"),
                    @ApiResponse(responseCode = "404", description = "Aucun compte actif portant cet identifiant dans l'établissement courant")
            })
    @PostMapping("/{id}/mot-de-passe-temporaire")
    @PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
    public MotDePasseTemporaireDto regenererMotDePasseTemporaire(@PathVariable UUID id) {
        return new MotDePasseTemporaireDto(utilisateurService.regenererMotDePasseTemporaire(id));
    }

    @Operation(summary = "Change le mot de passe du compte authentifié",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Mot de passe changé"),
                    @ApiResponse(responseCode = "401", description = "Ancien mot de passe incorrect")
            })
    @PostMapping("/moi/mot-de-passe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changerMonMotDePasse(
            @AuthenticationPrincipal UtilisateurPrincipal principal,
            @Valid @RequestBody ChangerMotDePasseRequestDto requete) {
        utilisateurService.changerMotDePasseSoiMeme(
                principal.utilisateurId(), requete.ancienMotDePasse(), requete.nouveauMotDePasse());
        return ResponseEntity.noContent().build();
    }
}
