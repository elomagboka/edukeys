package tg.novadigital.edukeys.academique.domain;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Affectation d'une {@link Matiere} à un {@link Niveau}, pour une
 * {@link Filiere} optionnelle (US-03) : une seule table de liaison couvrant
 * le couple (niveau, filière), plutôt que deux listes distinctes rattachées
 * à la matière — décision produit, sinon le système de séries obtiendrait un
 * produit cartésien faux (une matière listée pour un niveau ET pour une
 * filière d'un autre niveau serait alors affectée partout). {@code filiere}
 * {@code null} signifie « ce niveau, toutes filières » (collège, où les
 * filières n'existent pas encore).
 *
 * <p>Relation unidirectionnelle : aucune collection inverse sur {@link
 * Matiere}, {@link Niveau} ni {@link Filiere} — ces deux dernières entités ne
 * sont pas modifiées par cette US.</p>
 *
 * <p><b>Note technique (US-03), à traiter par US-20</b> : cette affectation
 * (coefficient, volume horaire, obligatoire) est permanente, non versionnée
 * par année scolaire. Modifier un coefficient modifie donc rétroactivement ce
 * que verraient d'anciens bulletins recalculés après coup. US-20 (génération
 * des bulletins) devra figer les coefficients et moyennes au moment de la
 * publication du bulletin plutôt que de les recalculer depuis l'état courant
 * de {@code affectations_matieres}. Voir aussi la section US-20 de
 * {@code docs/backlog.md}.</p>
 *
 * <p>{@code @Audited} répété explicitement (voir {@code Filiere}).</p>
 */
@Entity
@Table(name = "affectations_matieres")
@Audited
public class AffectationMatiere extends EntiteEtablissement {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matiere_id", nullable = false)
    private Matiere matiere;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "niveau_id", nullable = false)
    private Niveau niveau;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "filiere_id")
    private Filiere filiere;

    @Column(name = "coefficient", precision = 4, scale = 2)
    private BigDecimal coefficient;

    @Column(name = "volume_horaire", precision = 4, scale = 1)
    private BigDecimal volumeHoraire;

    @Column(name = "obligatoire")
    private Boolean obligatoire;

    protected AffectationMatiere() {
    }

    public AffectationMatiere(UUID etablissementId, Matiere matiere, Niveau niveau, Filiere filiere,
                               BigDecimal coefficient, BigDecimal volumeHoraire, Boolean obligatoire) {
        super(etablissementId);
        this.matiere = matiere;
        this.niveau = niveau;
        this.filiere = filiere;
        this.coefficient = coefficient;
        this.volumeHoraire = volumeHoraire;
        this.obligatoire = obligatoire;
    }

    public void modifierAttributs(BigDecimal coefficient, BigDecimal volumeHoraire, Boolean obligatoire) {
        this.coefficient = coefficient;
        this.volumeHoraire = volumeHoraire;
        this.obligatoire = obligatoire;
    }

    public Matiere getMatiere() {
        return matiere;
    }

    public Niveau getNiveau() {
        return niveau;
    }

    public Filiere getFiliere() {
        return filiere;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public BigDecimal getVolumeHoraire() {
        return volumeHoraire;
    }

    public Boolean getObligatoire() {
        return obligatoire;
    }
}
