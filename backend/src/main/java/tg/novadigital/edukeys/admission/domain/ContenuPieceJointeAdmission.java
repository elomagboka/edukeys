package tg.novadigital.edukeys.admission.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Contenu binaire d'une pièce jointe d'admission (US-06, correction de revue
 * B3). {@code @Basic(fetch = LAZY)} seul sur {@code PieceJointeAdmission.contenu}
 * était inopérant sans instrumentation de bytecode Hibernate : le contenu
 * (jusqu'à 5 Mo par pièce, 15 Mo pour un dossier) était donc chargé à chaque
 * consultation du détail d'un dossier. Sortir le contenu dans une entité
 * séparée, à clé partagée ({@code @MapsId}), garantit qu'il n'est jamais
 * chargé tant que cette entité elle-même n'est pas explicitement demandée —
 * seul {@link tg.novadigital.edukeys.admission.service.StockagePiecesJointes}
 * la lit, et seulement pour le téléchargement.
 *
 * <p>Comme {@code PieceJointeAdmission}, seul {@link #contenu} est exclu de
 * l'audit Envers ; la table {@code _aud} correspondante n'a pas de colonne
 * {@code contenu}.</p>
 */
@Entity
@Table(name = "contenus_pieces_jointes_admission")
@Audited
public class ContenuPieceJointeAdmission extends EntiteEtablissement {

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private PieceJointeAdmission piece;

    /** Copie de contrôle de l'empreinte, indépendante de celle portée par {@code PieceJointeAdmission}. */
    @Column(name = "empreinte_sha256", nullable = false, length = 64)
    private String empreinteSha256;

    @NotAudited
    @Column(nullable = false)
    private byte[] contenu;

    protected ContenuPieceJointeAdmission() {
    }

    public ContenuPieceJointeAdmission(UUID etablissementId, PieceJointeAdmission piece, String empreinteSha256, byte[] contenu) {
        super(etablissementId);
        this.piece = piece;
        this.empreinteSha256 = empreinteSha256;
        this.contenu = contenu;
    }

    public PieceJointeAdmission getPiece() {
        return piece;
    }

    public String getEmpreinteSha256() {
        return empreinteSha256;
    }

    public byte[] getContenu() {
        return contenu;
    }
}
