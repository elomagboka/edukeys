package tg.novadigital.edukeys.academique.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Année scolaire d'un établissement (US-01). {@code etablissementId} reste
 * une colonne UUID scalaire héritée de {@link EntiteEtablissement}, jamais
 * une relation {@code @ManyToOne} : le module {@code academique} n'importe
 * jamais le module {@code etablissement} (CLAUDE.md, règle 1).
 *
 * <p>{@code @Audited} répété explicitement : ne se propage pas depuis
 * {@code EntiteEtablissement}/{@code BaseEntity} avec cette version
 * d'Hibernate Envers (voir {@code VerificateurAuditEnvers}).</p>
 *
 * <p>Les méthodes métier ci-dessous ne portent pas les validations de règle
 * métier (R1, R2, R7...) : c'est le rôle du service, seul habilité à lancer
 * les exceptions métier (CLAUDE.md, règle 8). L'entité se borne à garantir
 * qu'elle ne peut pas être mise dans un état interne incohérent.</p>
 */
@Entity
@Table(name = "annees_scolaires")
@Audited
public class AnneeScolaire extends EntiteEtablissement {

    @Column(nullable = false, length = 20)
    private String libelle;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAnneeScolaire statut = StatutAnneeScolaire.PREPARATION;

    @Column(name = "date_cloture")
    private Instant dateCloture;

    @Column(name = "date_activation")
    private Instant dateActivation;

    protected AnneeScolaire() {
    }

    public AnneeScolaire(UUID etablissementId, String libelle, LocalDate dateDebut, LocalDate dateFin) {
        super(etablissementId);
        this.libelle = libelle;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = StatutAnneeScolaire.PREPARATION;
    }

    /** R9 : la vérification que l'année est modifiable (PREPARATION/ACTIVE) est faite par le service, avant l'appel. */
    public void modifierDates(LocalDate dateDebut, LocalDate dateFin, String libelle) {
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.libelle = libelle;
    }

    /** R8 : bascule PREPARATION -> ACTIVE. {@code dateActivation} n'est renseignée qu'à la toute première activation. */
    public void activer(Instant maintenant) {
        this.statut = StatutAnneeScolaire.ACTIVE;
        if (this.dateActivation == null) {
            this.dateActivation = maintenant;
        }
    }

    /** R10 : bascule ACTIVE -> CLOTUREE. */
    public void cloturer(Instant maintenant) {
        this.statut = StatutAnneeScolaire.CLOTUREE;
        this.dateCloture = maintenant;
    }

    public String getLibelle() {
        return libelle;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public StatutAnneeScolaire getStatut() {
        return statut;
    }

    public Instant getDateCloture() {
        return dateCloture;
    }

    public Instant getDateActivation() {
        return dateActivation;
    }
}
