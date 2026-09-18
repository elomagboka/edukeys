package tg.novadigital.edukeys.academique.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tg.novadigital.edukeys.academique.domain.Matiere;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface MatiereRepository extends BaseRepository<Matiere> {

    List<Matiere> findByEtablissementIdAndActifTrueOrderByLibelleAsc(UUID etablissementId);

    List<Matiere> findByEtablissementIdOrderByLibelleAsc(UUID etablissementId);

    boolean existsByEtablissementIdAndLibelleAndActifTrue(UUID etablissementId, String libelle);

    boolean existsByEtablissementIdAndCodeAndActifTrue(UUID etablissementId, String code);

    /**
     * Filtre {@code niveauId} de la liste (JPQL uniquement, CLAUDE.md règle 2) :
     * matières ayant au moins une affectation active sur ce niveau, quelle que
     * soit la filière (y compris {@code null}).
     */
    @Query("""
            select distinct m from Matiere m
            join AffectationMatiere a on a.matiere = m and a.actif = true
            where m.etablissementId = :etablissementId
              and a.niveau.id = :niveauId
              and (:inclureInactives = true or m.actif = true)
            order by m.libelle asc
            """)
    List<Matiere> findAffecteesAuNiveau(@Param("etablissementId") UUID etablissementId,
                                         @Param("niveauId") UUID niveauId,
                                         @Param("inclureInactives") boolean inclureInactives);

    /**
     * Filtre {@code filiereId} seul : affectations portant exactement cette
     * filière, <strong>plus</strong> les affectations « niveau seul » (filière
     * {@code null} = « ce niveau, toutes filières »), qui constituent le tronc
     * commun et forment l'essentiel des matières au lycée. Les exclure ferait
     * mentir le filtre : le Français affecté à « Terminale, toutes filières »
     * doit apparaître quand on filtre sur Terminale A.
     *
     * <p>Un niveau relève de la filière quand il partage son cycle ; une filière
     * sans cycle (rattachement optionnel, D4) n'exclut aucun niveau.</p>
     */
    @Query("""
            select distinct m from Matiere m
            join AffectationMatiere a on a.matiere = m and a.actif = true
            join a.niveau n
            left join a.filiere f
            where m.etablissementId = :etablissementId
              and (f.id = :filiereId
                   or (f is null and exists (select 1 from Filiere fc
                                             where fc.id = :filiereId
                                               and (fc.cycle is null or fc.cycle.id = n.cycle.id))))
              and (:inclureInactives = true or m.actif = true)
            order by m.libelle asc
            """)
    List<Matiere> findAffecteesALaFiliere(@Param("etablissementId") UUID etablissementId,
                                           @Param("filiereId") UUID filiereId,
                                           @Param("inclureInactives") boolean inclureInactives);

    /**
     * Filtres {@code niveauId} et {@code filiereId} combinés (« Terminale A ») :
     * affectations de ce niveau portant cette filière ou aucune. La cohérence de
     * cycle est déjà garantie à l'affectation, le niveau suffit donc à cadrer les
     * lignes « toutes filières ».
     */
    @Query("""
            select distinct m from Matiere m
            join AffectationMatiere a on a.matiere = m and a.actif = true
            join a.niveau n
            left join a.filiere f
            where m.etablissementId = :etablissementId
              and n.id = :niveauId
              and (f is null or f.id = :filiereId)
              and (:inclureInactives = true or m.actif = true)
            order by m.libelle asc
            """)
    List<Matiere> findAffecteesAuNiveauEtALaFiliere(@Param("etablissementId") UUID etablissementId,
                                                     @Param("niveauId") UUID niveauId,
                                                     @Param("filiereId") UUID filiereId,
                                                     @Param("inclureInactives") boolean inclureInactives);
}
