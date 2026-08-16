package tg.novadigital.edukeys.common.domain;

import java.time.Instant;

/**
 * Contrat de désactivation logique. Aucune entité du projet n'est physiquement
 * supprimée : voir la règle d'architecture correspondante dans CLAUDE.md.
 */
public interface EntiteDesactivable {

    boolean isActif();

    Instant getDateDesactivation();

    void desactiver();
}
