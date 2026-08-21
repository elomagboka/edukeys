package tg.novadigital.edukeys.identite.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tg.novadigital.edukeys.identite.domain.RoleCode;

/**
 * Résout, à chaque requête authentifiée, les permissions effectives d'un
 * ensemble de codes de rôle. Union en mémoire (correction T-04, lot 1 n°2) :
 * {@link RoleCode} porte directement ses permissions, aucun accès base n'est
 * nécessaire — condition posée par la spec T-04 pour ne pas payer une requête
 * (ou un N+1) sur chaque appel d'API protégé.
 */
@Component
public class PermissionResolver {

    /** Union des permissions de tous les rôles fournis (arbitrage T-04 n°1). */
    public Set<String> resoudrePermissions(Set<String> codesRoles) {
        if (codesRoles == null || codesRoles.isEmpty()) {
            return Set.of();
        }
        return codesRoles.stream()
                .map(RoleCode::valueOf)
                .flatMap(role -> role.getPermissions().stream())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
