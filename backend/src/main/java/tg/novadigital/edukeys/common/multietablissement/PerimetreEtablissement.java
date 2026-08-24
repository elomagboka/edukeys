package tg.novadigital.edukeys.common.multietablissement;

import java.util.UUID;

/**
 * Établissement courant porté par {@link ContexteEtablissement}, avec sa
 * provenance : utile au diagnostic (un contexte issu d'une bascule
 * {@code SUPER_ADMIN} n'a pas les mêmes implications de sécurité qu'un
 * contexte résolu depuis le JWT).
 */
public record PerimetreEtablissement(UUID etablissementId, Origine origine) {

    public enum Origine {
        /** Résolu depuis le claim {@code eta} du JWT (cas nominal). */
        JETON,
        /** Ouvert explicitement par un traitement asynchrone ou planifié. */
        EXPLICITE,
        /** Un {@code SUPER_ADMIN} a basculé sur un établissement pour l'administrer. */
        BASCULE_SUPER_ADMIN
    }
}
