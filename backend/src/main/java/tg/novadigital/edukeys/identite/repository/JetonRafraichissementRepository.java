package tg.novadigital.edukeys.identite.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;

public interface JetonRafraichissementRepository extends BaseRepository<JetonRafraichissement> {

    /** Quel que soit son état : seul moyen de distinguer un jeton inconnu d'un jeton rejoué. */
    Optional<JetonRafraichissement> findByJetonHache(String jetonHache);

    List<JetonRafraichissement> findByUtilisateurIdAndActifTrue(UUID utilisateurId);

    List<JetonRafraichissement> findByFamilleIdAndActifTrue(UUID familleId);
}
