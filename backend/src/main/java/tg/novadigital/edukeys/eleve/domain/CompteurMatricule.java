package tg.novadigital.edukeys.eleve.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Dernier numéro de séquence de matricule attribué pour une année (US-08) : une ligne par
 * (établissement, année de DÉBUT de l'année scolaire), incrémentée sous verrou pessimiste (JPQL).
 *
 * <p><strong>Jamais désactivé</strong> : la ligne porte l'historique de la séquence, la désactiver ferait
 * repartir la numérotation et réattribuer des matricules. Unicité ABSOLUE en base (exception assumée à
 * la règle 4 de CLAUDE.md) et {@code CHECK (actif = TRUE)} ; la requête de verrouillage ne filtre donc pas
 * sur {@code actif}.</p>
 */
@Entity
@Table(name = "compteurs_matricule")
@Audited
public class CompteurMatricule extends EntiteEtablissement {

    @Column(nullable = false, updatable = false)
    private int annee;

    @Column(nullable = false)
    private long dernier;

    protected CompteurMatricule() {
    }

    public CompteurMatricule(UUID etablissementId, int annee) {
        super(etablissementId);
        this.annee = annee;
        this.dernier = 0;
    }

    /** Incrémente et retourne le nouveau numéro — appelé sous verrou pessimiste, séquence non épuisée (vérifiée par {@code GenerateurMatricule}). */
    public long incrementerEtObtenir() {
        this.dernier = this.dernier + 1;
        return this.dernier;
    }

    @Override
    public void desactiver() {
        throw new UnsupportedOperationException("Un compteur de matricule ne se désactive jamais : la séquence serait réattribuée.");
    }

    public int getAnnee() {
        return annee;
    }

    public long getDernier() {
        return dernier;
    }
}
