package tg.novadigital.edukeys.admission.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Journal en ajout seul des décisions prises sur un dossier d'admission
 * (US-07) : une ligne par décision, jamais modifiée ni désactivée en usage
 * normal. {@code demandeId} est un identifiant scalaire, jamais une relation
 * {@code @ManyToOne} (CLAUDE.md, règle 1 — même patron que
 * {@code DemandeAdmission#anneeScolaireId}). Aucun setter métier : une
 * décision, une fois créée, est immuable.
 */
@Entity
@Table(name = "decisions_admission")
@Audited
public class DecisionAdmission extends EntiteEtablissement {

    @Column(name = "demande_id", nullable = false, updatable = false)
    private UUID demandeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_precedent", nullable = false, length = 20, updatable = false)
    private StatutAdmission statutPrecedent;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_nouveau", nullable = false, length = 20, updatable = false)
    private StatutAdmission statutNouveau;

    /** Note interne (US-07), jamais transmise au parent ni incluse dans une notification. */
    @Column(length = 500, updatable = false)
    private String observation;

    @Column(name = "decide_par", nullable = false, updatable = false)
    private UUID decidePar;

    @Column(name = "date_decision", nullable = false, updatable = false)
    private Instant dateDecision;

    protected DecisionAdmission() {
    }

    public DecisionAdmission(
            UUID etablissementId,
            UUID demandeId,
            StatutAdmission statutPrecedent,
            StatutAdmission statutNouveau,
            String observation,
            UUID decidePar,
            Instant dateDecision) {
        super(etablissementId);
        this.demandeId = demandeId;
        this.statutPrecedent = statutPrecedent;
        this.statutNouveau = statutNouveau;
        this.observation = observation;
        this.decidePar = decidePar;
        this.dateDecision = dateDecision;
    }

    public UUID getDemandeId() {
        return demandeId;
    }

    public StatutAdmission getStatutPrecedent() {
        return statutPrecedent;
    }

    public StatutAdmission getStatutNouveau() {
        return statutNouveau;
    }

    public String getObservation() {
        return observation;
    }

    public UUID getDecidePar() {
        return decidePar;
    }

    public Instant getDateDecision() {
        return dateDecision;
    }
}
