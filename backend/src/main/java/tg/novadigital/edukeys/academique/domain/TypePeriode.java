package tg.novadigital.edukeys.academique.domain;

/**
 * Type d'une période académique (US-05). Coexistence volontairement libre
 * sur une même année scolaire (R6) : un collège en trimestres et un lycée en
 * semestres au sein du même établissement est un cas normal, jamais interdit
 * ici. Ne descend pas au niveau du cycle — chantier US-19/US-20.
 */
public enum TypePeriode {
    TRIMESTRE,
    SEMESTRE
}
