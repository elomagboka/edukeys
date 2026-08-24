package tg.novadigital.edukeys.common.domain;

import java.util.UUID;

import org.hibernate.annotations.Filter;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * Socle des entités métier cloisonnées par établissement
 * (docs/adr/0002-multi-etablissement.md). {@code etablissementId} est une
 * simple colonne UUID scalaire, jamais une relation {@code @ManyToOne} : le
 * module {@code common} ne dépend d'aucun module métier, et le module
 * {@code etablissement} ne doit jamais être importé ailleurs (CLAUDE.md,
 * règle 1). La contrainte de clé étrangère est posée en base uniquement
 * (migration Flyway), jamais en Java.
 *
 * <p>Le filtre Hibernate {@code filtreEtablissement} (défini dans
 * {@link tg.novadigital.edukeys.common.multietablissement}) est déclaré ici et
 * armé automatiquement sur chaque session
 * (voir {@code common.multietablissement}). Ne JAMAIS ajouter {@code site_id}
 * à ce filtre : le site est une organisation interne à l'établissement, pas
 * un second niveau de cloisonnement de sécurité — la direction doit pouvoir
 * voir tous ses sites (docs/adr/0005-sites-et-annexes.md).</p>
 *
 * <p>{@code BaseEntity} reste le socle direct de {@code Etablissement},
 * {@code Utilisateur}, {@code AffectationEtablissement} et
 * {@code JetonRafraichissement} : ces quatre entités sont lues pendant
 * l'authentification, avant l'ouverture de tout contexte d'établissement — un
 * filtre sur elles casserait le login.</p>
 */
@MappedSuperclass
@Filter(name = "filtreEtablissement", condition = "etablissement_id = :etablissementId")
@EntityListeners(RemplisseurEtablissement.class)
public abstract class EntiteEtablissement extends BaseEntity {

    @Column(name = "etablissement_id", nullable = false, updatable = false)
    private UUID etablissementId;

    protected EntiteEtablissement() {
    }

    protected EntiteEtablissement(UUID etablissementId) {
        this.etablissementId = etablissementId;
    }

    public UUID getEtablissementId() {
        return etablissementId;
    }

    /**
     * <b>Package-private, et cela fait partie du dispositif</b> : seul
     * {@link RemplisseurEtablissement}, qui vit dans ce package pour cette
     * raison précise, peut écrire ce champ. Les entités métier résidant toutes
     * dans d'autres packages, le compilateur leur en interdit l'accès — la
     * règle « personne n'écrit {@code etablissement_id} à la main »
     * (ADR-0002, §2) n'est donc pas une convention. Ne pas élargir cette
     * visibilité, pas même pour un test : une sous-classe passe par le
     * constructeur {@code protected} ci-dessus.
     */
    void setEtablissementId(UUID etablissementId) {
        this.etablissementId = etablissementId;
    }
}
