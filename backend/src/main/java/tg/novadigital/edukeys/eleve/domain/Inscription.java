package tg.novadigital.edukeys.eleve.domain;

import java.time.Instant;
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
 * Inscription d'un élève dans une classe pour une année scolaire (US-08). Niveau et filière ne sont PAS
 * recopiés : ils se lisent sur la classe. {@code anneeScolaireId}, {@code classeId} et {@code siteId} sont
 * des identifiants scalaires, jamais des relations vers {@code academique} / {@code etablissement}
 * (CLAUDE.md, règle 1) ; les clés étrangères sont posées en base.
 */
@Entity
@Table(name = "inscriptions")
@Audited
public class Inscription extends EntiteEtablissement {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleve_id", nullable = false, updatable = false)
    private Eleve eleve;

    @Column(name = "annee_scolaire_id", nullable = false, updatable = false)
    private UUID anneeScolaireId;

    @Column(name = "classe_id", nullable = false)
    private UUID classeId;

    /**
     * Site de la classe À LA DATE DE L'INSCRIPTION (CLAUDE.md, règle 9 : organisation interne, jamais un
     * filtre de sécurité). <strong>Peut diverger</strong> du site actuel de la classe si celui-ci change
     * ensuite : ce changement n'est pas contrôlé avant US-11 (transferts), voir {@code ClasseService#modifier}.
     */
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "date_inscription", nullable = false)
    private Instant dateInscription;

    protected Inscription() {
    }

    public Inscription(UUID etablissementId, Eleve eleve, UUID anneeScolaireId, UUID classeId, UUID siteId,
                       Instant dateInscription) {
        super(etablissementId);
        this.eleve = eleve;
        this.anneeScolaireId = anneeScolaireId;
        this.classeId = classeId;
        this.siteId = siteId;
        this.dateInscription = dateInscription;
    }

    public Eleve getEleve() {
        return eleve;
    }

    public UUID getAnneeScolaireId() {
        return anneeScolaireId;
    }

    public UUID getClasseId() {
        return classeId;
    }

    public UUID getSiteId() {
        return siteId;
    }

    public Instant getDateInscription() {
        return dateInscription;
    }
}
