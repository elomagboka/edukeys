package tg.novadigital.edukeys.academique;

import java.util.Optional;
import java.util.UUID;

/**
 * Seul point d'entrée public du module {@code academique} pour les autres
 * modules (US-02/03/05 et modules aval) — R14, CLAUDE.md règle 1 : aucun
 * import de {@link tg.novadigital.edukeys.academique.domain.AnneeScolaire}
 * hors du module {@code academique}, jamais.
 *
 * <p>Opère sur l'établissement du contexte courant
 * ({@code ContexteEtablissement}) : aucune méthode ne prend
 * {@code etablissementId} en paramètre.</p>
 */
public interface AnneeScolaireQuery {

    /** Identifiant de l'année active de l'établissement courant, si une existe (A1). */
    Optional<UUID> idAnneeActive();

    /**
     * Une année est modifiable en écriture par les modules aval si son statut
     * n'est ni introuvable ni {@code CLOTUREE} (R11 : la clôture d'une année
     * scolaire rend toute donnée qui lui est rattachée en lecture seule).
     */
    boolean estModifiable(UUID anneeId);
}
