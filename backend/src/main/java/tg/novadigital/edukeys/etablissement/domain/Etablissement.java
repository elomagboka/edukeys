package tg.novadigital.edukeys.etablissement.domain;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Établissement client d'Edukeys : socle du cloisonnement de sécurité
 * (docs/adr/0002-multi-etablissement.md). Entité volontairement minimale en
 * T-05 — enrichie (logo, adresse, modèle d'initialisation du référentiel...)
 * en T-10 (US-00).
 *
 * <p>Reste directement sous {@link BaseEntity}, pas sous
 * {@code EntiteEtablissement} : le filtre Hibernate multi-établissement ne
 * s'applique jamais à l'entité qui définit les établissements eux-mêmes.</p>
 */
@Entity
@Table(name = "etablissements")
@Audited
public class Etablissement extends BaseEntity {

    @Column(nullable = false, unique = false)
    private String code;

    @Column(nullable = false)
    private String nom;

    protected Etablissement() {
    }

    public Etablissement(String code, String nom) {
        this.code = code;
        this.nom = nom;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }
}
