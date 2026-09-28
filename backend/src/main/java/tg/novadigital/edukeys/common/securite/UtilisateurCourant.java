package tg.novadigital.edukeys.common.securite;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissementAbsentException;

/**
 * Résout l'identifiant de l'utilisateur authentifié courant, sans que
 * {@code common} n'ait à dépendre du module {@code identite} (CLAUDE.md,
 * règle 1) : passe par {@link PrincipalAuditable}, déjà porté par
 * {@code UtilisateurPrincipal}.
 */
public final class UtilisateurCourant {

    private UtilisateurCourant() {
    }

    /**
     * @throws ContexteEtablissementAbsentException si aucun utilisateur authentifié n'est résolvable
     *         — réutilise le même code d'erreur (403) que l'absence de contexte d'établissement :
     *         un appelant qui atteint ce point sans authentification exploitable est dans le même
     *         cas d'usage anormal.
     */
    public static UUID exigerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PrincipalAuditable principal) {
            return UUID.fromString(principal.identifiantAudit());
        }
        throw new ContexteEtablissementAbsentException();
    }
}
