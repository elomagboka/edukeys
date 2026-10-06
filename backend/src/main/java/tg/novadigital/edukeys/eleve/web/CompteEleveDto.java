package tg.novadigital.edukeys.eleve.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Accès du compte élève, rendu UNE SEULE FOIS à l'inscription : le mot de passe temporaire n'est jamais
 * stocké en clair, jamais envoyé par SMS ni par notification (US-08, Q10). {@link #toString()} le masque.
 */
public record CompteEleveDto(
        @Schema(description = "Identifiant de connexion : le matricule, en minuscules.")
        String identifiantConnexion,
        @Schema(description = "Mot de passe temporaire, affiché une seule fois ; à changer à la première connexion.")
        String motDePasseTemporaire,
        @Schema(description = "Date d'expiration du mot de passe temporaire.")
        Instant expiration) {

    @Override
    public String toString() {
        return "CompteEleveDto[identifiantConnexion=" + identifiantConnexion + ", motDePasseTemporaire=***, expiration=" + expiration + "]";
    }
}
