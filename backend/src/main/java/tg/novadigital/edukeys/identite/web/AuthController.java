package tg.novadigital.edukeys.identite.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tg.novadigital.edukeys.identite.security.UtilisateurPrincipal;
import tg.novadigital.edukeys.identite.service.AuthService;

/**
 * Authentification par mot de passe, rotation de jeton et bascule
 * d'établissement actif. Seuls {@code /login} et {@code /refresh} sont
 * ouverts (voir {@code SecurityConfig}) : la limitation de débit fait l'objet
 * d'une issue séparée, hors périmètre de T-04. {@code /etablissement-actif}
 * exige une authentification valide.
 */
@Tag(name = "Authentification")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Connexion par email et mot de passe",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Jetons émis"),
                    @ApiResponse(responseCode = "401", description = "Identifiants invalides")
            })
    @PostMapping("/login")
    @SecurityRequirements
    public JetonsReponseDto login(@Valid @RequestBody LoginRequestDto requete, HttpServletRequest request) {
        return authService.connecter(requete.email(), requete.motDePasse(), request.getRemoteAddr());
    }

    @Operation(summary = "Rafraîchissement de l'access token à partir d'un refresh token valide",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Nouveaux jetons émis"),
                    @ApiResponse(responseCode = "401", description = "Jeton de rafraîchissement invalide, expiré ou révoqué")
            })
    @PostMapping("/refresh")
    @SecurityRequirements
    public JetonsReponseDto refresh(@Valid @RequestBody RefreshRequestDto requete) {
        return authService.rafraichir(requete.refreshToken());
    }

    @Operation(summary = "Bascule l'établissement actif du compte authentifié",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Nouveaux jetons émis pour l'établissement demandé"),
                    @ApiResponse(responseCode = "403", description = "Aucune affectation active sur cet établissement")
            })
    @PostMapping("/etablissement-actif")
    @PreAuthorize("isAuthenticated()")
    public JetonsReponseDto basculerEtablissement(
            @AuthenticationPrincipal UtilisateurPrincipal principal,
            @Valid @RequestBody BasculerEtablissementRequestDto requete) {
        return authService.basculerEtablissement(principal.utilisateurId(), requete.etablissementId());
    }
}
