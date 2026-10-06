package tg.novadigital.edukeys.academique.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
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

    /** US-06 : résolution en lot des libellés de classes (CLAUDE.md, règle 10). */
    List<Classe> findByIdIn(List<UUID> ids);

    Optional<Classe> findByIdAndEtablissementIdAndActifTrue(UUID id, UUID etablissementId);

    /**
     * US-08 : verrou pessimiste sur la SEULE ligne {@code classes} (pas de jointure, pas de
     * {@code @EntityGraph} : les relations restent LAZY et ne sont pas verrouillées).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Classe c where c.id = :id and c.etablissementId = :etablissementId")
    Optional<Classe> trouverPourVerrouiller(@Param("id") UUID id, @Param("etablissementId") UUID etablissementId);

    /** US-08 : libellés de la classe en UNE requête, sans verrou (à appeler après {@link #trouverPourVerrouiller}). */
    @Query("""
            select new tg.novadigital.edukeys.academique.repository.ClasseRepository$LibellesClasse(
                n.id, n.libelle, f.id, f.libelle, a.id, a.libelle, a.dateDebut, a.statut)
            from Classe c
            join c.niveau n
            left join c.filiere f
            join c.anneeScolaire a
            where c.id = :id
            """)
    Optional<LibellesClasse> lireLibelles(@Param("id") UUID id);

    record LibellesClasse(UUID niveauId, String niveauLibelle, UUID filiereId, String filiereLibelle,
                          UUID anneeScolaireId, String anneeLibelle, java.time.LocalDate anneeDateDebut,
                          tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire anneeStatut) {
    }
}
