package tg.novadigital.edukeys.academique.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Matière du référentiel académique (US-03). Existe indépendamment de ses
 * affectations à un niveau/filière (voir {@link AffectationMatiere}) : pas de
 * collection {@code @OneToMany} inverse ici, sur le même principe que
 * {@code Cycle}/{@code Niveau} — la lecture des affectations d'une matière
 * passe par {@code AffectationMatiereRepository}, jamais par une navigation
 * en boucle.
 *
 * <p>{@code @Audited} répété explicitement (voir {@code Filiere}).</p>
 */
@Entity
@Table(name = "matieres")
@Audited
public class Matiere extends EntiteEtablissement {

    @Column(nullable = false, length = 80)
    private String libelle;

    @Column(length = 20)
    private String code;

    protected Matiere() {
    }

    public Matiere(UUID etablissementId, String libelle, String code) {
        super(etablissementId);
        this.libelle = libelle;
        this.code = code;
    }

    public void modifier(String libelle, String code) {
        this.libelle = libelle;
        this.code = code;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCode() {
        return code;
    }
}
