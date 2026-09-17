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
     * Filtre {@code filiereId} : uniquement les affectations dont la filière
     * est exactement celle-ci (pas les affectations « niveau seul », qui ne se
     * rattachent à aucune filière précise — voir la javadoc du service).
     */
    @Query("""
            select distinct m from Matiere m
            join AffectationMatiere a on a.matiere = m and a.actif = true
            where m.etablissementId = :etablissementId
              and a.filiere.id = :filiereId
              and (:inclureInactives = true or m.actif = true)
            order by m.libelle asc
            """)
    List<Matiere> findAffecteesALaFiliere(@Param("etablissementId") UUID etablissementId,
                                           @Param("filiereId") UUID filiereId,
                                           @Param("inclureInactives") boolean inclureInactives);
}
