package tg.novadigital.edukeys.etablissement.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Logo d'un établissement (US-00). Le remplacement d'un logo ne met jamais à
 * jour la ligne existante : l'ancien est désactivé logiquement, un nouveau
 * créé (R12), pour garder l'historique de qui a changé le logo et quand — ce
 * qui suppose que {@code contenu} lui-même reste hors de cet historique
 * (voir {@code @NotAudited} ci-dessous), sinon chaque révision porterait la
 * copie complète du fichier binaire dans la table {@code _aud}.
 *
 * <p><b>Seul {@link #contenu} est exclu de l'audit</b> ({@code @NotAudited}) :
 * le reste de l'entité (nom de fichier, type MIME, date/auteur de
 * remplacement, empreinte) reste audité normalement.</p>
 */
@Entity
@Table(name = "logos_etablissement")
@Audited
public class LogoEtablissement extends EntiteEtablissement {

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "type_mime", nullable = false, length = 100)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false)
    private int tailleOctets;

    @Column(name = "empreinte_sha256", nullable = false, length = 64)
    private String empreinteSha256;

    // Pas de @Lob : sur PostgreSQL, @Lob mapperait byte[] sur OID (large object,
    // hors ligne, jamais nettoyé automatiquement) au lieu de BYTEA (colonne en
    // ligne, adaptée à un logo plafonné à 1 Mo) — @Lob omis pour laisser
    // Hibernate 6 mapper byte[] sur VARBINARY/BYTEA par défaut (schéma V6).
    @NotAudited
    @Column(nullable = false)
    private byte[] contenu;

    protected LogoEtablissement() {
    }

    public LogoEtablissement(UUID etablissementId, String nomFichier, String typeMime,
                              int tailleOctets, String empreinteSha256, byte[] contenu) {
        super(etablissementId);
        this.nomFichier = nomFichier;
        this.typeMime = typeMime;
        this.tailleOctets = tailleOctets;
        this.empreinteSha256 = empreinteSha256;
        this.contenu = contenu;
    }

    /** Symétrique de {@code desactiver()} (hérité de {@code BaseEntity}) : réactivation du logo lors de la réactivation en cascade de son établissement. */
    public void reactiver() {
        reactiverLogiquement();
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getTypeMime() {
        return typeMime;
    }

    public int getTailleOctets() {
        return tailleOctets;
    }

    public String getEmpreinteSha256() {
        return empreinteSha256;
    }

    public byte[] getContenu() {
        return contenu;
    }
}
