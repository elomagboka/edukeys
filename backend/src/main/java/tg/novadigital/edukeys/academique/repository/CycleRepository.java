package tg.novadigital.edukeys.academique.repository;

import java.util.List;
import java.util.UUID;

import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface CycleRepository extends BaseRepository<Cycle> {

    List<Cycle> findByEtablissementIdAndActifTrueOrderByRangAsc(UUID etablissementId);

    List<Cycle> findByEtablissementIdOrderByRangAsc(UUID etablissementId);

    boolean existsByEtablissementIdAndLibelleAndActifTrue(UUID etablissementId, String libelle);

    boolean existsByEtablissementIdAndCodeAndActifTrue(UUID etablissementId, String code);

    boolean existsByEtablissementIdAndRangAndActifTrue(UUID etablissementId, int rang);
}
