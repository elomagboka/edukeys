package tg.novadigital.edukeys.admission.domain;

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
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.texte.NormalisationTexte;

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

    /**
     * EN_ATTENTE -> {ACCEPTEE, REFUSEE, LISTE_ATTENTE} ; LISTE_ATTENTE ->
     * {ACCEPTEE, REFUSEE} ; REFUSEE -> {LISTE_ATTENTE, ACCEPTEE} (un refus est
     * corrigible, ex. clic erroné) ; ACCEPTEE et ANNULEE sont finales (US-07).
     */
    private static final Map<StatutAdmission, Set<StatutAdmission>> TRANSITIONS_AUTORISEES = new EnumMap<>(Map.of(
            StatutAdmission.EN_ATTENTE,
            EnumSet.of(StatutAdmission.ACCEPTEE, StatutAdmission.REFUSEE, StatutAdmission.LISTE_ATTENTE),
            StatutAdmission.LISTE_ATTENTE, EnumSet.of(StatutAdmission.ACCEPTEE, StatutAdmission.REFUSEE),
            StatutAdmission.REFUSEE, EnumSet.of(StatutAdmission.LISTE_ATTENTE, StatutAdmission.ACCEPTEE),
            StatutAdmission.ACCEPTEE, EnumSet.noneOf(StatutAdmission.class),
            StatutAdmission.ANNULEE, EnumSet.noneOf(StatutAdmission.class)));

    @Column(nullable = false, length = 20)
    private String reference;

    /**
     * Code de suivi opaque, seule valeur rendue au parent sur le canal public
     * (3e revue, point 1) : la référence séquentielle {@code PRE-AAAA-NNNNNN}
     * laissait deviner, en comparant les numéros, qu'un enfant avait déjà un
     * dossier dans l'école. Aléatoire cryptographique, jamais ordonnable —
     * voir {@code GenerateurCodeSuiviAdmission}.
     */
    @Column(name = "code_suivi", nullable = false, length = 32, updatable = false)
    private String codeSuivi;

    @Column(name = "annee_scolaire_id", nullable = false, updatable = false)
    private UUID anneeScolaireId;

    @Column(name = "niveau_id", nullable = false)
    private UUID niveauId;

    @Column(name = "classe_id")
    private UUID classeId;

    @Column(nullable = false, length = 100)
    private String nom;

    /**
     * I5 : comparaison d'idempotence insensible à la casse et aux accents, calculée
     * en Java (jamais en SQL, CLAUDE.md règle 2).
     */
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

    /**
     * Hachage SHA-256 salé, à sens unique — jamais l'IP en clair, aucun moyen de la
     * retrouver (règle 4 de la spec US-06).
     */
    @Column(name = "ip_soumission_hash", length = 64)
    private String ipSoumissionHash;

    /**
     * Élève né de ce dossier (US-08), identifiant scalaire (jamais une relation vers
     * {@code eleve}, CLAUDE.md règle 1). Renseigné avec {@link #dateInscription}, une seule fois.
     */
    @Column(name = "eleve_id")
    private UUID eleveId;

    @Column(name = "date_inscription")
    private Instant dateInscription;

    @Version
    @Column(nullable = false)
    private long version;

    protected DemandeAdmission() {
    }

    public DemandeAdmission(
            UUID etablissementId,
            String reference,
            String codeSuivi,
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
        this.codeSuivi = codeSuivi;
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

    /** Délègue à {@link NormalisationTexte} (partagée avec le module {@code eleve}, US-08). */
    public static String normaliserPourComparaison(String valeur) {
        return NormalisationTexte.normaliserPourComparaison(valeur);
    }

    public String getNomNormalise() {
        return nomNormalise;
    }

    public String getPrenomsNormalises() {
        return prenomsNormalises;
    }

    /**
     * Machine à états (US-07) : lève {@link RegleMetierViolee} sur toute transition
     * non autorisée.
     */
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

    /**
     * Garde-fou d'état (US-08) : seul un dossier ACCEPTEE peut devenir un élève, une seule fois. Le service
     * contrôle ces règles avec des codes d'erreur précis avant l'appel ; cette méthode empêche seulement
     * qu'un futur appelant contourne la règle.
     */
    public void marquerInscrite(UUID eleveId, Instant instant) {
        if (this.statut != StatutAdmission.ACCEPTEE) {
            throw new RegleMetierViolee(CodeErreur.INSCRIPTION_DEMANDE_NON_ACCEPTEE,
                    "Seul un dossier accepté peut donner lieu à une inscription.");
        }
        if (this.eleveId != null) {
            throw new ConflitException(CodeErreur.INSCRIPTION_DEJA_EFFECTUEE, "Ce dossier a déjà donné lieu à une inscription.");
        }
        this.eleveId = eleveId;
        this.dateInscription = instant;
    }

    public UUID getEleveId() {
        return eleveId;
    }

    public Instant getDateInscription() {
        return dateInscription;
    }

    public boolean estModifiable() {
        return this.statut == StatutAdmission.EN_ATTENTE;
    }

    public String getCodeSuivi() {
        return codeSuivi;
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
