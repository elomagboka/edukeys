package tg.novadigital.edukeys.admission.repository;

import java.util.Optional;
import java.util.UUID;

import tg.novadigital.edukeys.admission.domain.ContenuPieceJointeAdmission;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface ContenuPieceJointeAdmissionRepository extends BaseRepository<ContenuPieceJointeAdmission> {

    Optional<ContenuPieceJointeAdmission> findByPieceId(UUID pieceId);
}
