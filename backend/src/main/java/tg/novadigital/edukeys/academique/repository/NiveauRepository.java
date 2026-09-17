package tg.novadigital.edukeys.academique.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;

import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface NiveauRepository extends BaseRepository<Niveau> {

    @EntityGraph(attributePaths = "cycle")
    List<Niveau> findByEtablissementIdAndActifTrueOrderByCycle_RangAscRangAsc(UUID etablissementId);

    @EntityGraph(attributePaths = "cycle")
    List<Niveau> findByEtablissementIdOrderByCycle_RangAscRangAsc(UUID etablissementId);

    @EntityGraph(attributePaths = "cycle")
    List<Niveau> findByEtablissementIdAndCycleIdAndActifTrueOrderByCycle_RangAscRangAsc(UUID etablissementId, UUID cycleId);

    @EntityGraph(attributePaths = "cycle")
    List<Niveau> findByEtablissementIdAndCycleIdOrderByCycle_RangAscRangAsc(UUID etablissementId, UUID cycleId);

    boolean existsByEtablissementIdAndLibelleAndActifTrue(UUID etablissementId, String libelle);

    boolean existsByEtablissementIdAndCodeAndActifTrue(UUID etablissementId, String code);

    boolean existsByEtablissementIdAndRangAndActifTrue(UUID etablissementId, int rang);

    long countByEtablissementIdAndCycleIdAndActifTrue(UUID etablissementId, UUID cycleId);
}
