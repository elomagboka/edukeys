package tg.novadigital.edukeys.identite.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Refresh token opaque (pas un JWT), persisté haché — jamais en clair, comme
 * un mot de passe (arbitrage T-04 n°3). La désactivation logique héritée de
 * {@link BaseEntity} (via {@code desactiver()}) sert de mécanisme de
 * révocation : un jeton révoqué a {@code actif = false}.
 *
 * <p>{@code familleId} relie toute la chaîne de rotation issue d'un même
 * login : présenter un jeton déjà révoqué (rejeu) est le signal classique
 * d'un vol de jeton, et révoque immédiatement toute la famille, pas seulement
 * le jeton présenté (correction T-04, lot 1 n°4).</p>
 *
 * <p>{@code etablissementActifId} mémorise l'établissement actif au moment de
 * l'émission : sans lui, chaque rafraîchissement réinitialiserait
 * silencieusement l'utilisateur sur son premier établissement affecté,
 * annulant toute bascule précédente (correction T-04, lot 2 n°8).</p>
 */
@Entity
@Table(name = "jetons_rafraichissement")
@Audited
public class JetonRafraichissement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "jeton_hache", nullable = false)
    private String jetonHache;

    @Column(name = "famille_id", nullable = false)
    private UUID familleId;

    @Column(name = "etablissement_actif_id")
    private UUID etablissementActifId;

    @Column(name = "date_expiration", nullable = false)
    private Instant dateExpiration;

    protected JetonRafraichissement() {
    }

    /** Nouvelle chaîne de rotation (login initial) : famille fraîchement créée, sans établissement mémorisé. */
    public JetonRafraichissement(Utilisateur utilisateur, String jetonHache, Instant dateExpiration) {
        this(utilisateur, jetonHache, dateExpiration, UUID.randomUUID(), null);
    }

    /** Poursuite d'une chaîne de rotation existante, sans établissement mémorisé. */
    public JetonRafraichissement(Utilisateur utilisateur, String jetonHache, Instant dateExpiration, UUID familleId) {
        this(utilisateur, jetonHache, dateExpiration, familleId, null);
    }

    public JetonRafraichissement(
            Utilisateur utilisateur,
            String jetonHache,
            Instant dateExpiration,
            UUID familleId,
            UUID etablissementActifId) {
        this.utilisateur = utilisateur;
        this.jetonHache = jetonHache;
        this.dateExpiration = dateExpiration;
        this.familleId = familleId;
        this.etablissementActifId = etablissementActifId;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public String getJetonHache() {
        return jetonHache;
    }

    public UUID getFamilleId() {
        return familleId;
    }

    public UUID getEtablissementActifId() {
        return etablissementActifId;
    }

    public Instant getDateExpiration() {
        return dateExpiration;
    }

    public boolean estExpire() {
        return Instant.now().isAfter(dateExpiration);
    }
}
