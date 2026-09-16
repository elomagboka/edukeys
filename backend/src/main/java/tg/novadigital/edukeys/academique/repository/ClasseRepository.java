package tg.novadigital.edukeys.academique.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface ClasseRepository extends BaseRepository<Classe> {

    /**
     * Filtre de liste unique (R9 spec), plutôt que de multiplier les méthodes
     * dérivées : {@code anneeScolaireId}, {@code niveauId}, {@code filiereId},
     * {@code siteId} sont tous nullable — le contrôle applicatif (R11, siteId
     * jamais fait confiance) reste dans le service, pas ici.
     *
     * <p>{@code @EntityGraph} obligatoire (point d'attention « N+1 » de la
     * spec) : {@code niveau}, {@code niveau.cycle}, {@code filiere},
     * {@code anneeScolaire} chargés en une seule requête, quel que soit le
     * nombre de classes retournées.</p>
     */
    @EntityGraph(attributePaths = {"niveau", "niveau.cycle", "filiere", "anneeScolaire"})
    @Query("""
            select c from Classe c
            where c.etablissementId = :etablissementId
              and (:anneeScolaireId is null or c.anneeScolaire.id = :anneeScolaireId)
              and (:niveauId is null or c.niveau.id = :niveauId)
              and (:filiereId is null or c.filiere.id = :filiereId)
              and (:siteId is null or c.siteId = :siteId)
              and (:inclureInactives = true or c.actif = true)
            order by c.niveau.rang asc, c.libelle asc
            """)
    List<Classe> rechercher(
            @Param("etablissementId") UUID etablissementId,
            @Param("anneeScolaireId") UUID anneeScolaireId,
            @Param("niveauId") UUID niveauId,
            @Param("filiereId") UUID filiereId,
            @Param("siteId") UUID siteId,
            @Param("inclureInactives") boolean inclureInactives);

    @EntityGraph(attributePaths = {"niveau", "niveau.cycle", "filiere", "anneeScolaire"})
    Optional<Classe> findWithGraphById(UUID id);

    boolean existsByEtablissementIdAndAnneeScolaireIdAndLibelleAndActifTrue(
            UUID etablissementId, UUID anneeScolaireId, String libelle);

    /** R13 : classes actives portées par un niveau, toutes années confondues. */
    long countByEtablissementIdAndNiveauIdAndActifTrue(UUID etablissementId, UUID niveauId);

    /** R13 : classes actives référençant une filière, toutes années confondues. */
    long countByEtablissementIdAndFiliereIdAndActifTrue(UUID etablissementId, UUID filiereId);
}
