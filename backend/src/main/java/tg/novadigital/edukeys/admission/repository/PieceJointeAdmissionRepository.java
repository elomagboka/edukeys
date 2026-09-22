package tg.novadigital.edukeys.admission.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface PieceJointeAdmissionRepository extends BaseRepository<PieceJointeAdmission> {

    List<PieceJointeAdmission> findByDemandeIdAndActifTrue(UUID demandeId);

    Optional<PieceJointeAdmission> findByIdAndDemandeIdAndActifTrue(UUID id, UUID demandeId);

    boolean existsByDemandeIdAndEmpreinteSha256AndActifTrue(UUID demandeId, String empreinteSha256);

    long countByDemandeIdAndActifTrue(UUID demandeId);
}
