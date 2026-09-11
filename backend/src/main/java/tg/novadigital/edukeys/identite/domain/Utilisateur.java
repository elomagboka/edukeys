package tg.novadigital.edukeys.identite.domain;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Compte d'accès à Edukeys. L'email est unique globalement, pas par
 * établissement (docs/adr/0002-multi-etablissement.md) : un utilisateur, un
 * compte, qui peut porter plusieurs {@link AffectationEtablissement}.
 */
@Entity
@Table(name = "utilisateurs")
@Audited
public class Utilisateur extends BaseEntity {

    @Column(nullable = false, unique = false)
    private String email;

    @Column(name = "mot_de_passe_hache", nullable = false)
    private String motDePasseHache;

    @Column(name = "nom_complet", nullable = false)
    private String nomComplet;

    /** Hors périmètre établissement : voir docs/adr/0002-multi-etablissement.md, §5. */
    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin;

    /**
     * Changement de mot de passe obligatoire au premier accès (US-04) : posé à
     * {@code true} lorsqu'un mot de passe temporaire vient d'être émis
     * (création de compte ou régénération), jusqu'à ce que le titulaire le
     * change lui-même via {@code POST /api/v1/utilisateurs/moi/mot-de-passe}.
     * Tant qu'il est vrai, une garde centrale
     * ({@code identite.security.GardeMotDePasseAChangerFilter}) refuse tout
     * autre endpoint métier — jamais reproduite contrôleur par contrôleur.
     */
    @Column(name = "mot_de_passe_a_changer", nullable = false)
    private boolean motDePasseAChanger;

    @OneToMany(mappedBy = "utilisateur", fetch = FetchType.LAZY)
    private Set<AffectationEtablissement> affectations = new HashSet<>();

    protected Utilisateur() {
    }

    public Utilisateur(String email, String motDePasseHache, String nomComplet, boolean superAdmin) {
        this.email = email;
        this.motDePasseHache = motDePasseHache;
        this.nomComplet = nomComplet;
        this.superAdmin = superAdmin;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHache() {
        return motDePasseHache;
    }

    public void changerMotDePasseHache(String motDePasseHache) {
        this.motDePasseHache = motDePasseHache;
    }

    public String getNomComplet() {
        return nomComplet;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public boolean isMotDePasseAChanger() {
        return motDePasseAChanger;
    }

    /** Posé lors de l'émission d'un mot de passe temporaire (création de compte ou régénération, US-04). */
    public void exigerChangementMotDePasse() {
        this.motDePasseAChanger = true;
    }

    /** Levé lorsque le titulaire a lui-même changé son mot de passe (US-04). */
    public void confirmerChangementMotDePasse() {
        this.motDePasseAChanger = false;
    }

    /**
     * Réactivation logique (US-04) : pendant de {@link #desactiver()},
     * exposée publiquement comme le prévoit la Javadoc de
     * {@link tg.novadigital.edukeys.common.domain.BaseEntity#reactiverLogiquement()}.
     */
    public void reactiver() {
        reactiverLogiquement();
    }

    public Set<AffectationEtablissement> getAffectations() {
        return affectations;
    }
}
