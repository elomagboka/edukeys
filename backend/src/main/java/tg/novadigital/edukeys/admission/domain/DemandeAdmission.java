package tg.novadigital.edukeys.admission.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;

/**
 * Dossier de pré-inscription en ligne (US-06). {@code anneeScolaireId},
 * {@code niveauId} et {@code classeId} sont des identifiants scalaires,
 * jamais une relation JPA vers {@code academique} (CLAUDE.md, règle 1) : la
 * cohérence est vérifiée par {@code OffreAdmissionQuery.verifierChoix} avant
 * la création, jamais recontrôlée par une contrainte de base.
 */
@Entity
@Table(name = "demandes_admission")
@Audited
public class DemandeAdmission extends EntiteEtablissement {

    /** EN_ATTENTE -> {ACCEPTEE, REFUSEE, LISTE_ATTENTE} ; LISTE_ATTENTE -> {ACCEPTEE, REFUSEE} ; les autres sont finales (US-07). */
    private static final Map<StatutAdmission, Set<StatutAdmission>> TRANSITIONS_AUTORISEES = new EnumMap<>(Map.of(
            StatutAdmission.EN_ATTENTE, EnumSet.of(StatutAdmission.ACCEPTEE, StatutAdmission.REFUSEE, StatutAdmission.LISTE_ATTENTE),
            StatutAdmission.LISTE_ATTENTE, EnumSet.of(StatutAdmission.ACCEPTEE, StatutAdmission.REFUSEE),
            StatutAdmission.ACCEPTEE, EnumSet.noneOf(StatutAdmission.class),
            StatutAdmission.REFUSEE, EnumSet.noneOf(StatutAdmission.class),
            StatutAdmission.ANNULEE, EnumSet.noneOf(StatutAdmission.class)));

    @Column(nullable = false, length = 20)
    private String reference;

    @Column(name = "annee_scolaire_id", nullable = false, updatable = false)
    private UUID anneeScolaireId;

    @Column(name = "niveau_id", nullable = false)
    private UUID niveauId;

    @Column(name = "classe_id")
    private UUID classeId;

    @Column(nullable = false, length = 100)
    private String nom;

    /** I5 : comparaison d'idempotence insensible à la casse et aux accents, calculée en Java (jamais en SQL, CLAUDE.md règle 2). */
    @Column(name = "nom_normalise", nullable = false, length = 100)
    private String nomNormalise;

    @Column(nullable = false, length = 150)
    private String prenoms;

    @Column(name = "prenoms_normalises", nullable = false, length = 150)
    private String prenomsNormalises;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "lieu_naissance", length = 100)
    private String lieuNaissance;

    @Column(length = 1)
    private String sexe;

    @Column(length = 2)
    private String nationalite;

    @Column(name = "etablissement_origine", length = 150)
    private String etablissementOrigine;

    @Column(name = "responsable_nom", nullable = false, length = 100)
    private String responsableNom;

    @Column(name = "responsable_prenoms", nullable = false, length = 150)
    private String responsablePrenoms;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsable_lien", nullable = false, length = 10)
    private LienResponsable responsableLien;

    @Column(name = "responsable_telephone", nullable = false, length = 20)
    private String responsableTelephone;

    @Column(name = "responsable_email", length = 254)
    private String responsableEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAdmission statut = StatutAdmission.EN_ATTENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CanalAdmission canal;

    @Column(name = "date_soumission", nullable = false)
    private Instant dateSoumission;

    @Column(name = "date_decision")
    private Instant dateDecision;

    @Column(name = "motif_decision", length = 500)
    private String motifDecision;

    @Column(name = "decide_par")
    private UUID decidePar;

    @Column(name = "consentement_donnees_at")
    private Instant consentementDonneesAt;

    /** Hachage SHA-256 salé, à sens unique — jamais l'IP en clair, aucun moyen de la retrouver (règle 4 de la spec US-06). */
    @Column(name = "ip_soumission_hash", length = 64)
    private String ipSoumissionHash;

    @Version
    @Column(nullable = false)
    private long version;

    protected DemandeAdmission() {
    }

    public DemandeAdmission(
            UUID etablissementId,
            String reference,
            UUID anneeScolaireId,
            UUID niveauId,
            UUID classeId,
            String nom,
            String prenoms,
            LocalDate dateNaissance,
            String lieuNaissance,
            String sexe,
            String nationalite,
            String etablissementOrigine,
            String responsableNom,
            String responsablePrenoms,
            LienResponsable responsableLien,
            String responsableTelephone,
            String responsableEmail,
            CanalAdmission canal,
            Instant dateSoumission,
            Instant consentementDonneesAt,
            String ipSoumissionHash) {
        super(etablissementId);
        this.reference = reference;
        this.anneeScolaireId = anneeScolaireId;
        this.niveauId = niveauId;
        this.classeId = classeId;
        this.nom = nom;
        this.nomNormalise = normaliserPourComparaison(nom);
        this.prenoms = prenoms;
        this.prenomsNormalises = normaliserPourComparaison(prenoms);
        this.dateNaissance = dateNaissance;
        this.lieuNaissance = lieuNaissance;
        this.sexe = sexe;
        this.nationalite = nationalite;
        this.etablissementOrigine = etablissementOrigine;
        this.responsableNom = responsableNom;
        this.responsablePrenoms = responsablePrenoms;
        this.responsableLien = responsableLien;
        this.responsableTelephone = responsableTelephone;
        this.responsableEmail = responsableEmail;
        this.canal = canal;
        this.dateSoumission = dateSoumission;
        this.consentementDonneesAt = consentementDonneesAt;
        this.ipSoumissionHash = ipSoumissionHash;
        this.statut = StatutAdmission.EN_ATTENTE;
    }

    /**
     * Normalisation Java (I5) pour la comparaison d'idempotence : sans
     * accents (décomposition NFD, retrait des marques diacritiques) et en
     * majuscules — jamais une fonction SQL native (CLAUDE.md, règle 2).
     */
    public static String normaliserPourComparaison(String valeur) {
        if (valeur == null) {
            return null;
        }
        String sansAccents = Normalizer.normalize(valeur, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public String getNomNormalise() {
        return nomNormalise;
    }

    public String getPrenomsNormalises() {
        return prenomsNormalises;
    }

    /** Machine à états (US-07) : lève {@link RegleMetierViolee} sur toute transition non autorisée. */
    public void changerStatut(StatutAdmission cible, String motif, UUID decidePar, Instant maintenant) {
        Set<StatutAdmission> autorisees = TRANSITIONS_AUTORISEES.getOrDefault(this.statut, Set.of());
        if (!autorisees.contains(cible)) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_TRANSITION_INVALIDE,
                    "Transition de statut invalide : %s -> %s.".formatted(this.statut, cible));
        }
        this.statut = cible;
        this.motifDecision = motif;
        this.decidePar = decidePar;
        this.dateDecision = maintenant;
    }

    public boolean estModifiable() {
        return this.statut == StatutAdmission.EN_ATTENTE;
    }

    public String getReference() {
        return reference;
    }

    public UUID getAnneeScolaireId() {
        return anneeScolaireId;
    }

    public UUID getNiveauId() {
        return niveauId;
    }

    public UUID getClasseId() {
        return classeId;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenoms() {
        return prenoms;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public String getLieuNaissance() {
        return lieuNaissance;
    }

    public String getSexe() {
        return sexe;
    }

    public String getNationalite() {
        return nationalite;
    }

    public String getEtablissementOrigine() {
        return etablissementOrigine;
    }

    public String getResponsableNom() {
        return responsableNom;
    }

    public String getResponsablePrenoms() {
        return responsablePrenoms;
    }

    public LienResponsable getResponsableLien() {
        return responsableLien;
    }

    public String getResponsableTelephone() {
        return responsableTelephone;
    }

    public String getResponsableEmail() {
        return responsableEmail;
    }

    public StatutAdmission getStatut() {
        return statut;
    }

    public CanalAdmission getCanal() {
        return canal;
    }

    public Instant getDateSoumission() {
        return dateSoumission;
    }

    public Instant getDateDecision() {
        return dateDecision;
    }

    public String getMotifDecision() {
        return motifDecision;
    }

    public UUID getDecidePar() {
        return decidePar;
    }

    public Instant getConsentementDonneesAt() {
        return consentementDonneesAt;
    }

    public String getIpSoumissionHash() {
        return ipSoumissionHash;
    }

    public long getVersion() {
        return version;
    }
}
