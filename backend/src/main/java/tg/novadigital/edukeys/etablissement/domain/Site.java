package tg.novadigital.edukeys.etablissement.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Site (annexe) d'un établissement (US-00, docs/adr/0005-sites-et-annexes.md).
 * Organise l'intérieur d'un établissement — classes, salles, inscriptions —
 * ce n'est PAS un second niveau de cloisonnement de sécurité : la direction
 * voit tous ses sites, {@code site_id} n'entre jamais dans le filtre
 * Hibernate multi-établissement.
 *
 * <p>Pas de relation JPA {@code @ManyToOne} vers {@code Etablissement} :
 * {@code etablissementId} scalaire hérité de {@link EntiteEtablissement}, la
 * FK n'est posée qu'en base (migration Flyway).</p>
 *
 * <p>{@code @Audited} répété explicitement : ne se propage pas depuis
 * {@code EntiteEtablissement}/{@code BaseEntity} avec cette version
 * d'Hibernate Envers (voir {@code VerificateurAuditEnvers}).</p>
 */
@Entity
@Table(name = "sites")
@Audited
public class Site extends EntiteEtablissement {

    /** Unique par établissement parmi les sites actifs (R6), normalisé en majuscules par le service. */
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String nom;

    /** Exactement un site principal actif par établissement (R4), garanti par index partiel ET logique service. */
    @Column(nullable = false)
    private boolean principal = false;

    private String ville;

    private String quartier;

    @Column(name = "adresse_ligne")
    private String adresseLigne;

    @Column(length = 30)
    private String telephone;

    protected Site() {
    }

    public Site(UUID etablissementId, String code, String nom, boolean principal,
                String ville, String quartier, String adresseLigne, String telephone) {
        super(etablissementId);
        this.code = code;
        this.nom = nom;
        this.principal = principal;
        this.ville = ville;
        this.quartier = quartier;
        this.adresseLigne = adresseLigne;
        this.telephone = telephone;
    }

    /** Symétrique de {@code desactiver()} (hérité de {@code BaseEntity}) : réactivation d'un site lors de la réactivation en cascade de son établissement (R4). */
    public void reactiver() {
        reactiverLogiquement();
    }

    public void modifier(String nom, String ville, String quartier, String adresseLigne, String telephone) {
        this.nom = nom;
        this.ville = ville;
        this.quartier = quartier;
        this.adresseLigne = adresseLigne;
        this.telephone = telephone;
    }

    public void designerPrincipal() {
        this.principal = true;
    }

    public void retirerPrincipal() {
        this.principal = false;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public String getVille() {
        return ville;
    }

    public String getQuartier() {
        return quartier;
    }

    public String getAdresseLigne() {
        return adresseLigne;
    }

    public String getTelephone() {
        return telephone;
    }
}
