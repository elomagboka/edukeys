package tg.novadigital.edukeys.academique.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Cycle du référentiel académique (US-02, D1 : hiérarchie Cycle {@literal >}
 * Niveau {@literal >} Classe). Référentiel pérenne, sans {@code annee_scolaire_id}
 * (D2). Pas de collection inverse {@code @OneToMany} vers {@link Niveau}
 * (point d'attention 9 de la spec) : les comptages passent par une requête
 * d'agrégation dédiée, jamais par une navigation en boucle.
 *
 * <p>{@code @Audited} répété explicitement : ne se propage pas depuis
 * {@code EntiteEtablissement}/{@code BaseEntity} avec cette version
 * d'Hibernate Envers (voir {@code VerificateurAuditEnvers}).</p>
 */
@Entity
@Table(name = "cycles")
@Audited
public class Cycle extends EntiteEtablissement {

    @Column(nullable = false, length = 60)
    private String libelle;

    @Column(length = 20)
    private String code;

    @Column(nullable = false)
    private int rang;

    protected Cycle() {
    }

    public Cycle(UUID etablissementId, String libelle, String code, int rang) {
        super(etablissementId);
        this.libelle = libelle;
        this.code = code;
        this.rang = rang;
    }

    public void modifier(String libelle, String code, int rang) {
        this.libelle = libelle;
        this.code = code;
        this.rang = rang;
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
}
