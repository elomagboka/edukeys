package tg.novadigital.edukeys.academique.domain;

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
 * Période académique (trimestre ou semestre) d'une année scolaire (US-05).
 * {@code anneeScolaireId} reste une colonne UUID scalaire, jamais une
 * relation {@code @ManyToOne} : même principe que {@link AnneeScolaire}
 * elle-même vis-à-vis d'{@code Etablissement} (CLAUDE.md, règle 1 — ici les
 * deux entités vivent dans le même module, mais la navigation reste
 * volontairement écartée pour ne pas charger l'année à chaque lecture d'une
 * période).
 *
 * <p><strong>Trous autorisés entre périodes</strong> (vacances de Noël,
 * etc.) : aucune contrainte de couverture continue n'est posée, ni ici ni au
 * service — seul le non-chevauchement (contrainte d'exclusion SQL) est
 * garanti. Le rejet d'une note ou d'une absence saisie hors période sera
 * traité en US-15/US-18 ; la saisie d'une note y précisera sa période, avec
 * la période courante par défaut.</p>
 *
 * <p>{@code @Audited} répété explicitement (voir {@code AnneeScolaire}).</p>
 *
 * <p>Les méthodes métier ci-dessous ne portent aucune validation de règle
 * métier : c'est le rôle du service (CLAUDE.md, règle 8).</p>
 */
@Entity
@Table(name = "periodes_academiques")
@Audited
public class PeriodeAcademique extends EntiteEtablissement {

    @Column(name = "annee_scolaire_id", nullable = false, updatable = false)
    private UUID anneeScolaireId;

    @Column(nullable = false, length = 40)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TypePeriode type;

    @Column(nullable = false)
    private int ordre;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    protected PeriodeAcademique() {
    }

    public PeriodeAcademique(UUID etablissementId, UUID anneeScolaireId, String libelle, TypePeriode type,
                              int ordre, LocalDate dateDebut, LocalDate dateFin) {
        super(etablissementId);
        this.anneeScolaireId = anneeScolaireId;
        this.libelle = libelle;
        this.type = type;
        this.ordre = ordre;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    /** R9 : {@code type} et {@code anneeScolaireId} sont immuables après création, vérifié par le service en amont. */
    public void modifier(String libelle, LocalDate dateDebut, LocalDate dateFin, int ordre) {
        this.libelle = libelle;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.ordre = ordre;
    }

    public UUID getAnneeScolaireId() {
        return anneeScolaireId;
    }

    public String getLibelle() {
        return libelle;
    }

    public TypePeriode getType() {
        return type;
    }

    public int getOrdre() {
        return ordre;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }
}
