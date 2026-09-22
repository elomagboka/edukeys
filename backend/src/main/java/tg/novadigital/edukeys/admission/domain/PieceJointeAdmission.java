package tg.novadigital.edukeys.admission.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Pièce jointe d'une demande d'admission (US-06). Le contenu binaire ne vit
 * plus ici (correction de revue B3) : voir {@link ContenuPieceJointeAdmission}.
 * Cette entité elle-même ne porte donc plus aucune colonne BYTEA et peut être
 * chargée sans risque au détail d'un dossier.
 */
@Entity
@Table(name = "pieces_jointes_admission")
@Audited
public class PieceJointeAdmission extends EntiteEtablissement {

    @Column(name = "demande_id", nullable = false, updatable = false)
    private UUID demandeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_piece", nullable = false, length = 20)
    private TypePieceAdmission typePiece;

    @Column(name = "nom_original", nullable = false, length = 255)
    private String nomOriginal;

    @Column(name = "type_mime", nullable = false, length = 50)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false)
    private long tailleOctets;

    @Column(name = "empreinte_sha256", nullable = false, length = 64)
    private String empreinteSha256;

    protected PieceJointeAdmission() {
    }

    public PieceJointeAdmission(
            UUID etablissementId,
            UUID demandeId,
            TypePieceAdmission typePiece,
            String nomOriginal,
            String typeMime,
            long tailleOctets,
            String empreinteSha256) {
        super(etablissementId);
        this.demandeId = demandeId;
        this.typePiece = typePiece;
        this.nomOriginal = nomOriginal;
        this.typeMime = typeMime;
        this.tailleOctets = tailleOctets;
        this.empreinteSha256 = empreinteSha256;
    }

    public UUID getDemandeId() {
        return demandeId;
    }

    public TypePieceAdmission getTypePiece() {
        return typePiece;
    }

    public String getNomOriginal() {
        return nomOriginal;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public long getTailleOctets() {
        return tailleOctets;
    }

    public String getEmpreinteSha256() {
        return empreinteSha256;
    }
}
