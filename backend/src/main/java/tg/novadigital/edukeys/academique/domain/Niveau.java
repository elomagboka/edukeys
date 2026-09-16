package tg.novadigital.edukeys.academique.domain;

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
 * Niveau du référentiel académique (US-02), rattaché à un {@link Cycle}.
 * Référentiel pérenne, sans {@code annee_scolaire_id} (D2). Pas de collection
 * inverse {@code @OneToMany} vers {@link Classe} : les comptages passent par
 * une requête d'agrégation dédiée (R16).
 *
 * <p>{@code @Audited} répété explicitement (voir {@code Cycle}).</p>
 */
@Entity
@Table(name = "niveaux")
@Audited
public class Niveau extends EntiteEtablissement {

    @Column(nullable = false, length = 60)
    private String libelle;

    @Column(length = 20)
    private String code;

    @Column(nullable = false)
    private int rang;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private Cycle cycle;

    protected Niveau() {
    }

    public Niveau(UUID etablissementId, String libelle, String code, int rang, Cycle cycle) {
        super(etablissementId);
        this.libelle = libelle;
        this.code = code;
        this.rang = rang;
        this.cycle = cycle;
    }

    public void modifier(String libelle, String code, int rang, Cycle cycle) {
        this.libelle = libelle;
        this.code = code;
        this.rang = rang;
        this.cycle = cycle;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCode() {
        return code;
    }

    public int getRang() {
        return rang;
    }

    public Cycle getCycle() {
        return cycle;
    }
}
