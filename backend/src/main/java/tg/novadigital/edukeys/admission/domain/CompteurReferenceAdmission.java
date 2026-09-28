package tg.novadigital.edukeys.admission.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Compteur du dernier numéro de séquence attribué pour une année de
 * référence (US-06, format {@code PRE-<annee>-<sequence>}). Une ligne par
 * (établissement, année) ; incrémentée sous verrou pessimiste (JPQL, jamais
 * de SQL natif sur une entité métier — CLAUDE.md, règle 2).
 */
@Entity
@Table(name = "compteurs_reference_admission")
@Audited
public class CompteurReferenceAdmission extends EntiteEtablissement {

    @Column(nullable = false)
    private int annee;

    @Column(nullable = false)
    private long dernier;

    protected CompteurReferenceAdmission() {
    }

    public CompteurReferenceAdmission(UUID etablissementId, int annee) {
        super(etablissementId);
        this.annee = annee;
        this.dernier = 0;
    }

    /** Incrémente et retourne le nouveau numéro de séquence — appelé sous verrou pessimiste par le repository. */
    public long incrementerEtObtenir() {
        this.dernier = this.dernier + 1;
        return this.dernier;
    }

    public int getAnnee() {
        return annee;
    }

    public long getDernier() {
        return dernier;
    }
}
