package tg.novadigital.edukeys.academique.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;

import tg.novadigital.edukeys.academique.domain.AffectationMatiere;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface AffectationMatiereRepository extends BaseRepository<AffectationMatiere> {

    /**
     * Anti N+1 (point bloquant de la spec) : toutes les affectations actives
     * d'un ensemble de matières en une seule requête, {@code niveau} et
     * {@code filiere} chargés par {@code @EntityGraph} — l'appelant assemble
     * ensuite en mémoire, sans requête supplémentaire par matière.
     */
    @EntityGraph(attributePaths = {"niveau", "filiere"})
    List<AffectationMatiere> findByEtablissementIdAndMatiereIdInAndActifTrue(UUID etablissementId, List<UUID> matiereIds);

    @EntityGraph(attributePaths = {"niveau", "filiere", "matiere"})
    List<AffectationMatiere> findByEtablissementIdAndMatiereIdAndActifTrue(UUID etablissementId, UUID matiereId);

    List<AffectationMatiere> findByEtablissementIdAndNiveauIdAndActifTrue(UUID etablissementId, UUID niveauId);

    List<AffectationMatiere> findByEtablissementIdAndFiliereIdAndActifTrue(UUID etablissementId, UUID filiereId);

    long countByEtablissementIdAndNiveauIdAndActifTrue(UUID etablissementId, UUID niveauId);

    long countByEtablissementIdAndFiliereIdAndActifTrue(UUID etablissementId, UUID filiereId);
}
