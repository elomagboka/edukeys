package tg.novadigital.edukeys.identite.domain;

import java.time.Instant;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Jeton d'activation d'un mot de passe temporaire (US-04). Aucun canal de
 * notification fiable n'existe avant le Sprint 10 (docs/adr/0006-notifications.md) :
 * plutôt qu'un lien d'activation envoyé par email, un mot de passe temporaire
 * est généré côté serveur, retourné une seule fois dans la réponse de
 * création de compte, et son hash seul est conservé ici — jamais le mot de
 * passe en clair, jamais relogué.
 *
 * <p>{@code motDePasseHache} est haché avec {@link tg.novadigital.edukeys.identite.service.JetonHacheur}
 * (SHA-256, comme un refresh token) : ce champ ne sert jamais à authentifier
 * un login (qui reste vérifié via {@code Utilisateur#motDePasseHache}, BCrypt),
 * seulement à tracer/consommer/invalider le jeton émis.</p>
 *
 * <p>La désactivation logique héritée de {@link BaseEntity} sert à invalider
 * un jeton devenu obsolète (régénération d'un nouveau mot de passe temporaire) ;
 * {@code dateConsommation} marque, distinctement, l'usage réussi du jeton
 * (changement de mot de passe effectué par le titulaire).</p>
 *
 * <p>Le modèle ne suppose nulle part que le secret est remis de la main à la
 * main : il restera valable tel quel lorsque le canal email s'y branchera
 * (Sprint 10).</p>
 */
@Entity
@Table(name = "jetons_activation_compte")
@Audited
public class JetonActivationCompte extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "mot_de_passe_hache", nullable = false)
    private String motDePasseHache;

    @Column(name = "date_expiration", nullable = false)
    private Instant dateExpiration;

    @Column(name = "date_consommation")
    private Instant dateConsommation;

    protected JetonActivationCompte() {
    }

    public JetonActivationCompte(Utilisateur utilisateur, String motDePasseHache, Instant dateExpiration) {
        this.utilisateur = utilisateur;
        this.motDePasseHache = motDePasseHache;
        this.dateExpiration = dateExpiration;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public String getMotDePasseHache() {
        return motDePasseHache;
    }

    public Instant getDateExpiration() {
        return dateExpiration;
    }

    public Instant getDateConsommation() {
        return dateConsommation;
    }

    public boolean estConsomme() {
        return dateConsommation != null;
    }

    public boolean estExpire() {
        return Instant.now().isAfter(dateExpiration);
    }

    /** Usage réussi (changement de mot de passe effectué) : distinct de la désactivation/invalidation administrative. */
    public void consommer() {
        this.dateConsommation = Instant.now();
    }
}
