package tg.novadigital.edukeys.eleve.repository;

import java.util.UUID;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.eleve.domain.Inscription;

public interface InscriptionRepository extends BaseRepository<Inscription> {

    /**
     * Effectif d'une classe (US-08) : inscriptions actives. À appeler SOUS le verrou de la ligne de
     * la classe ({@code ClasseInscriptionQuery#verrouillerPourInscription}), sinon deux inscriptions
     * simultanées dépassent l'effectif maximal. Index {@code idx_inscriptions_classe_actif}.
     */
    long countByClasseIdAndActifTrue(UUID classeId);
}
