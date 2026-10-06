package tg.novadigital.edukeys.eleve.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;
import tg.novadigital.edukeys.common.texte.NormalisationTexte;

/**
 * Élève d'un établissement (US-08) : identité recopiée du dossier d'admission, matricule et compte.
 * Volontairement SANS site (ADR-0005) : le site organise l'inscription, pas la personne.
 *
 * <p>Le matricule est un identifiant métier pérenne (bulletins, reçus) : non modifiable et à unicité
 * absolue en base ({@code uk_eleves_matricule}, sans {@code WHERE actif}) — un élève désactivé garde le
 * sien, qui n'est jamais réattribué (exception assumée à la règle 4 de CLAUDE.md).
 * {@code utilisateurId} et {@code demandeAdmissionId} sont des identifiants scalaires, jamais des
 * relations vers {@code identite} / {@code admission} (CLAUDE.md, règle 1).</p>
 *
 * <p>Responsables : US-09. Photo et pièces : US-10.</p>
 */
@Entity
@Table(name = "eleves")
@Audited
public class Eleve extends EntiteEtablissement {

    @Column(nullable = false, length = 64, updatable = false)
    private String matricule;

    @Column(nullable = false, length = 100)
    private String nom;

    /** Calculé en Java ({@link NormalisationTexte}), jamais en SQL : sert à la détection d'homonymes. */
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

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Column(name = "demande_admission_id")
    private UUID demandeAdmissionId;

    protected Eleve() {
    }

    public Eleve(UUID etablissementId, String matricule, String nom, String prenoms, LocalDate dateNaissance,
                 String lieuNaissance, String sexe, String nationalite, String etablissementOrigine,
                 UUID utilisateurId, UUID demandeAdmissionId) {
        super(etablissementId);
        this.matricule = matricule;
        this.nom = nom;
        this.nomNormalise = NormalisationTexte.normaliserPourComparaison(nom);
        this.prenoms = prenoms;
        this.prenomsNormalises = NormalisationTexte.normaliserPourComparaison(prenoms);
        this.dateNaissance = dateNaissance;
        this.lieuNaissance = lieuNaissance;
        this.sexe = sexe;
        this.nationalite = nationalite;
        this.etablissementOrigine = etablissementOrigine;
        this.utilisateurId = utilisateurId;
        this.demandeAdmissionId = demandeAdmissionId;
    }

    public String getMatricule() {
        return matricule;
    }

    public String getNom() {
        return nom;
    }

    public String getNomNormalise() {
        return nomNormalise;
    }

    public String getPrenoms() {
        return prenoms;
    }

    public String getPrenomsNormalises() {
        return prenomsNormalises;
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

    public UUID getUtilisateurId() {
        return utilisateurId;
    }

    public UUID getDemandeAdmissionId() {
        return demandeAdmissionId;
    }
}
