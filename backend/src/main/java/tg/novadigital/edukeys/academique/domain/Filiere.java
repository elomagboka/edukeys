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
 * Filière du référentiel académique (US-02), rattachement au cycle optionnel
 * (D4) : sert à filtrer les choix proposés à la saisie, pas à contraindre.
 * Référentiel pérenne, sans {@code annee_scolaire_id} (D2).
 *
 * <p>{@code @Audited} répété explicitement (voir {@code Cycle}).</p>
 */
@Entity
@Table(name = "filieres")
@Audited
public class Filiere extends EntiteEtablissement {

    @Column(nullable = false, length = 80)
    private String libelle;

    @Column(length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cycle_id")
    private Cycle cycle;

    protected Filiere() {
    }

    public Filiere(UUID etablissementId, String libelle, String code, Cycle cycle) {
        super(etablissementId);
        this.libelle = libelle;
        this.code = code;
        this.cycle = cycle;
    }

    public void modifier(String libelle, String code, Cycle cycle) {
        this.libelle = libelle;
        this.code = code;
        this.cycle = cycle;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCode() {
        return code;
    }

    public Cycle getCycle() {
        return cycle;
    }
}
