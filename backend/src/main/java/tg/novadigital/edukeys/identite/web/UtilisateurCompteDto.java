package tg.novadigital.edukeys.identite.web;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import tg.novadigital.edukeys.identite.domain.RoleCode;

/**
 * Compte d'un établissement précis, avec les rôles et le site (optionnel) de
 * son affectation sur cet établissement (US-04). Jamais l'entité JPA
 * elle-même (CLAUDE.md, règle 7) ; jamais {@code motDePasseHache}.
 */
public record UtilisateurCompteDto(
        UUID id,
        String email,
        String nomComplet,
        boolean actif,
        boolean motDePasseAChanger,
        Set<RoleCode> roles,
        UUID siteId,
        Instant dateCreation) {
}
