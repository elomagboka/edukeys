package tg.novadigital.edukeys.academique.domain;

/**
 * Statut d'avancement d'une année scolaire (US-01, arbitrage A3) : distinct
 * de {@code actif} (désactivation logique, orthogonale). Enum simple, pas
 * d'entité JPA — même raisonnement que {@code RoleCode}.
 */
public enum StatutAnneeScolaire {
    PREPARATION,
    ACTIVE,
    CLOTUREE
}
