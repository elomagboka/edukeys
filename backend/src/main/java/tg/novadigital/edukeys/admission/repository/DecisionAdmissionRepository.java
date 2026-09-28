package tg.novadigital.edukeys.admission.repository;

import java.util.List;
import java.util.UUID;

import tg.novadigital.edukeys.admission.domain.DecisionAdmission;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface DecisionAdmissionRepository extends BaseRepository<DecisionAdmission> {

    List<DecisionAdmission> findByDemandeIdAndActifTrueOrderByDateDecisionAsc(UUID demandeId);
}
