package tg.novadigital.edukeys.admission.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tg.novadigital.edukeys.admission.domain.ContenuPieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.TypePieceAdmission;
import tg.novadigital.edukeys.admission.repository.ContenuPieceJointeAdmissionRepository;
import tg.novadigital.edukeys.admission.repository.PieceJointeAdmissionRepository;

/**
 * Implémentation BYTEA (en base) de {@link StockagePiecesJointes} — voir la
 * javadoc de l'interface pour la bascule prévue. Le contenu binaire est
 * enregistré dans {@link ContenuPieceJointeAdmission}, une table séparée
 * (B3, revue) : {@link #lireContenu} est le seul point de lecture.
 */
@Service
public class StockagePiecesJointesBytea implements StockagePiecesJointes {

    private final PieceJointeAdmissionRepository pieceJointeAdmissionRepository;
    private final ContenuPieceJointeAdmissionRepository contenuPieceJointeAdmissionRepository;

    public StockagePiecesJointesBytea(
            PieceJointeAdmissionRepository pieceJointeAdmissionRepository,
            ContenuPieceJointeAdmissionRepository contenuPieceJointeAdmissionRepository) {
        this.pieceJointeAdmissionRepository = pieceJointeAdmissionRepository;
        this.contenuPieceJointeAdmissionRepository = contenuPieceJointeAdmissionRepository;
    }

    @Override
    public PieceJointeAdmission enregistrer(
            UUID etablissementId,
            UUID demandeId,
            TypePieceAdmission typePiece,
            String nomOriginal,
            String typeMime,
            byte[] contenu,
            String empreinteSha256) {
        PieceJointeAdmission piece = new PieceJointeAdmission(
                etablissementId, demandeId, typePiece, nomOriginal, typeMime, contenu.length, empreinteSha256);
        PieceJointeAdmission sauvee = pieceJointeAdmissionRepository.save(piece);
        contenuPieceJointeAdmissionRepository.save(
                new ContenuPieceJointeAdmission(etablissementId, sauvee, empreinteSha256, contenu));
        return sauvee;
    }

    @Override
    public List<PieceJointeAdmission> lister(UUID demandeId) {
        return pieceJointeAdmissionRepository.findByDemandeIdAndActifTrue(demandeId);
    }

    @Override
    public long compterActives(UUID demandeId) {
        return pieceJointeAdmissionRepository.countByDemandeIdAndActifTrue(demandeId);
    }

    @Override
    public Optional<PieceJointeAdmission> trouver(UUID demandeId, UUID pieceId) {
        return pieceJointeAdmissionRepository.findByIdAndDemandeIdAndActifTrue(pieceId, demandeId);
    }

    @Override
    public void desactiver(PieceJointeAdmission piece) {
        piece.desactiver();
        pieceJointeAdmissionRepository.save(piece);
    }

    @Override
    public Optional<byte[]> lireContenu(UUID pieceId) {
        return contenuPieceJointeAdmissionRepository.findByPieceId(pieceId).map(ContenuPieceJointeAdmission::getContenu);
    }
}
