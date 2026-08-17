package tg.novadigital.edukeys.common.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Entité factice, support du contrôleur de démonstration de T-03
 * (pagination, tri, filtres dynamiques). Ne porte aucune donnée métier réelle
 * et ne doit jamais être référencée par un module métier.
 */
@Entity
@Table(name = "demo_entites")
public class DemoEntite extends BaseEntity {

    @Column(nullable = false)
    private String libelle;

    private String categorie;

    private Integer quantite;

    protected DemoEntite() {
    }

    public DemoEntite(String libelle, String categorie, Integer quantite) {
        this.libelle = libelle;
        this.categorie = categorie;
        this.quantite = quantite;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCategorie() {
        return categorie;
    }

    public Integer getQuantite() {
        return quantite;
    }
}
