package tg.novadigital.edukeys.academique.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;

import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface FiliereRepository extends BaseRepository<Filiere> {

    @EntityGraph(attributePaths = "cycle")
    List<Filiere> findByEtablissementIdAndActifTrueOrderByLibelleAsc(UUID etablissementId);

    @EntityGraph(attributePaths = "cycle")
    List<Filiere> findByEtablissementIdOrderByLibelleAsc(UUID etablissementId);

    @EntityGraph(attributePaths = "cycle")
    List<Filiere> findByEtablissementIdAndCycleIdAndActifTrueOrderByLibelleAsc(UUID etablissementId, UUID cycleId);

    @EntityGraph(attributePaths = "cycle")
    List<Filiere> findByEtablissementIdAndCycleIdOrderByLibelleAsc(UUID etablissementId, UUID cycleId);

    boolean existsByEtablissementIdAndLibelleAndActifTrue(UUID etablissementId, String libelle);

    boolean existsByEtablissementIdAndCodeAndActifTrue(UUID etablissementId, String code);
}
