package tg.novadigital.edukeys.etablissement.domain;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Établissement client d'Edukeys : socle du cloisonnement de sécurité
 * (docs/adr/0002-multi-etablissement.md). Entité minimale en T-05, enrichie
 * en T-10 (US-00) avec l'identité complète, les coordonnées et le modèle
 * d'initialisation du référentiel pédagogique.
 *
 * <p>Reste directement sous {@link BaseEntity}, pas sous
 * {@code EntiteEtablissement} : le filtre Hibernate multi-établissement ne
 * s'applique jamais à l'entité qui définit les établissements eux-mêmes.</p>
 *
 * <p>Pas de setters champ-à-champ : toute modification passe par une méthode
 * métier ({@link #modifierIdentite}, {@link #modifierCoordonnees}) qui pose
 * les règles de validité au même endroit que le reste de la logique
 * métier — {@code EtablissementService} reste responsable des règles
 * transverses (unicité, immuabilité du code), l'entité de la cohérence de son
 * propre état.</p>
 */
@Entity
@Table(name = "etablissements")
@Audited
public class Etablissement extends BaseEntity {

    /** Immuable après création (R2) : jamais de setter, aucune méthode métier ne le modifie. */
    @Column(nullable = false, unique = false)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Column(length = 20)
    private String sigle;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_etablissement", nullable = false, length = 20)
    private TypeEtablissement typeEtablissement;

    @Column(nullable = false, length = 100)
    private String ville;

    @Column(length = 150)
    private String quartier;

    @Column(name = "boite_postale", length = 50)
    private String boitePostale;

    @Column(name = "adresse_ligne")
    private String adresseLigne;

    /** Normalisé en minuscules par {@code EtablissementService} avant persistance. */
    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 30)
    private String telephone;

    @Column(name = "site_web")
    private String siteWeb;

    /**
     * ISO 3166-1 alpha-2, stocké et affiché tel quel. <b>Non exploité
     * fonctionnellement à ce jour</b> (ADR-0008) : aucune règle métier ne doit
     * jamais tester cette valeur ({@code if (paysCode == "TG")} et
     * équivalents sont interdits en revue) — un comportement qui varie selon
     * le pays doit devenir un paramètre d'établissement, jamais un test sur
     * le code pays.
     */
    @Column(name = "pays_code", nullable = false, length = 2)
    private String paysCode = "TG";

    /**
     * Identifiant IANA, stocké et affiché tel quel. <b>Non appliqué à
     * l'affichage à ce jour</b> (ADR-0008, issue T-11 à ouvrir) : aucune
     * conversion de fuseau n'a lieu côté serveur.
     */
    @Column(name = "fuseau_horaire", nullable = false, length = 64)
    private String fuseauHoraire = "Africa/Lome";

    /**
     * ISO 4217, stocké et affiché tel quel. <b>N'implique aucune conversion</b>
     * (ADR-0008) : parité fixe XOF/XAF supposée, jamais de taux de change
     * calculé dans le code métier.
     */
    @Column(name = "devise_code", nullable = false, length = 3)
    private String deviseCode = "XOF";

    @Column(name = "langue_defaut", nullable = false, length = 10)
    private String langueDefaut = "fr";

    @Column(name = "referentiel_initialise", nullable = false)
    private boolean referentielInitialise = false;

    /**
     * Compteur dénormalisé des sites actifs (durcissement post-revue T-10) :
     * {@code nombreSites} de la liste paginée ne peut pas venir d'une requête
     * agrégée sur {@code Site}, une {@code EntiteEtablissement} soumise au
     * filtre Hibernate — un appelant SUPER_ADMIN sans contexte ouvert
     * obtiendrait toujours 0, une valeur fausse. Maintenu uniquement par
     * {@link #incrementerSitesActifs()} et {@link #decrementerSitesActifs()},
     * jamais par un setter public, aux seuls points qui font varier le compte
     * ({@code SiteService.creer}, {@code SiteService.desactiver}, la création
     * du site principal dans {@code EtablissementService.creer}).
     */
    @Column(name = "nombre_sites_actifs", nullable = false)
    private int nombreSitesActifs = 0;

    protected Etablissement() {
    }

    public Etablissement(String code, String nom, TypeEtablissement typeEtablissement, String ville, String email) {
        this.code = code;
        this.nom = nom;
        this.typeEtablissement = typeEtablissement;
        this.ville = ville;
        this.email = email;
    }

    public void modifierIdentite(String nom, String sigle, TypeEtablissement typeEtablissement) {
        this.nom = nom;
        this.sigle = sigle;
        this.typeEtablissement = typeEtablissement;
    }

    public void modifierCoordonnees(
            String ville,
            String quartier,
            String boitePostale,
            String adresseLigne,
            String email,
            String telephone,
            String siteWeb,
            String fuseauHoraire,
            String deviseCode,
            String langueDefaut) {
        this.ville = ville;
        this.quartier = quartier;
        this.boitePostale = boitePostale;
        this.adresseLigne = adresseLigne;
        this.email = email;
        this.telephone = telephone;
        this.siteWeb = siteWeb;
        this.fuseauHoraire = fuseauHoraire;
        this.deviseCode = deviseCode;
        this.langueDefaut = langueDefaut;
    }

    /** R7 : ne peut être appelé qu'une fois — {@code EtablissementService} vérifie {@link #isReferentielInitialise()} avant d'appeler. */
    public void marquerReferentielInitialise() {
        this.referentielInitialise = true;
    }

    /** R10 : les vérifications de conflit (code/email repris entre-temps) sont du ressort de {@code EtablissementService}, avant cet appel. */
    public void reactiver() {
        reactiverLogiquement();
    }

    /** Un site actif de plus (création d'un site, y compris le site principal). */
    public void incrementerSitesActifs() {
        this.nombreSitesActifs++;
    }

    /** Un site actif de moins (désactivation d'un site). Jamais négatif : signe d'une désynchronisation à corriger, pas à masquer. */
    public void decrementerSitesActifs() {
        if (this.nombreSitesActifs <= 0) {
            throw new IllegalStateException("Le compteur de sites actifs ne peut pas devenir négatif.");
        }
        this.nombreSitesActifs--;
    }


    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public String getSigle() {
        return sigle;
    }

    public TypeEtablissement getTypeEtablissement() {
        return typeEtablissement;
    }

    public String getVille() {
        return ville;
    }

    public String getQuartier() {
        return quartier;
    }

    public String getBoitePostale() {
        return boitePostale;
    }

    public String getAdresseLigne() {
        return adresseLigne;
    }

    public String getEmail() {
        return email;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getSiteWeb() {
        return siteWeb;
    }

    public String getPaysCode() {
        return paysCode;
    }

    public String getFuseauHoraire() {
        return fuseauHoraire;
    }

    public String getDeviseCode() {
        return deviseCode;
    }

    public String getLangueDefaut() {
        return langueDefaut;
    }

    public boolean isReferentielInitialise() {
        return referentielInitialise;
    }

    public int getNombreSitesActifs() {
        return nombreSitesActifs;
    }
}
