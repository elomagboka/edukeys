package tg.novadigital.edukeys.admission.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.TypePieceAdmission;

/**
 * Abstraction du stockage des pièces jointes d'admission (US-06). Seule
 * implémentation à ce jour : {@link StockagePiecesJointesBytea} (contenu en
 * base). Une bascule vers un stockage objet est planifiée une fois le volume
 * atteint (issue #90, seuil ~300 demandes) — cette interface est ce qui
 * permettra de la faire sans toucher au reste du service.
 */
public interface StockagePiecesJointes {

    PieceJointeAdmission enregistrer(
            UUID etablissementId,
            UUID demandeId,
            TypePieceAdmission typePiece,
            String nomOriginal,
            String typeMime,
            byte[] contenu,
            String empreinteSha256);

    List<PieceJointeAdmission> lister(UUID demandeId);

    /** Compte les pièces actives sans les charger (mineur, revue) — utilisé pour la limite de {@code AdmissionProperties#nombreMaxPieces}. */
    long compterActives(UUID demandeId);

    /** 3e revue, point 5 : détecte un doublon exact (même empreinte SHA-256) déjà attaché à ce dossier. */
    boolean existeDejaPourDemande(UUID demandeId, String empreinteSha256);

    Optional<PieceJointeAdmission> trouver(UUID demandeId, UUID pieceId);

    void desactiver(PieceJointeAdmission piece);

    /** B3 (revue) : seul point de lecture du contenu binaire, réservé au téléchargement. */
    Optional<byte[]> lireContenu(UUID pieceId);
}
