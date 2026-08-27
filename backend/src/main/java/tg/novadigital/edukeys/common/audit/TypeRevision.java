package tg.novadigital.edukeys.common.audit;

import org.hibernate.envers.RevisionType;

/**
 * Traduction de {@link RevisionType} (API Envers) vers un type propre à
 * Edukeys : les consommateurs de {@link HistoriqueService} ne doivent pas
 * dépendre directement de l'API Envers (CLAUDE.md, règle 7 — même logique
 * appliquée ici à une bibliothèque tierce plutôt qu'à une entité JPA).
 */
public enum TypeRevision {
    AJOUT,
    MODIFICATION,
    SUPPRESSION;

    static TypeRevision depuis(RevisionType type) {
        return switch (type) {
            case ADD -> AJOUT;
            case MOD -> MODIFICATION;
            case DEL -> SUPPRESSION;
        };
    }
}
