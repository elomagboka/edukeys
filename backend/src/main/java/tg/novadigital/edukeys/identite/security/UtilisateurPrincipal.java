package tg.novadigital.edukeys.identite.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal Spring Security porté par le contexte d'authentification, extrait
 * des revendications du JWT (sub, eta, roles) — jamais des permissions, qui
 * sont résolues côté serveur (arbitrage T-04 n°5).
 */
public record UtilisateurPrincipal(UUID utilisateurId, UUID etablissementId, Set<String> codesRoles) {
}
