package tg.novadigital.edukeys.identite.domain;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Affectation d'un {@link Utilisateur} à un établissement, porteuse de
 * l'ensemble des rôles cumulés pour ce couple (docs/adr/0002-multi-etablissement.md).
 *
 * <p><strong>Référence sans clé étrangère.</strong> {@code etablissementId}
 * n'est volontairement pas une relation JPA classique : l'entité
 * {@code Etablissement} n'existe pas encore (elle est créée en T-05). C'est le
 * seul endroit du module {@code identite} où une référence à une autre entité
 * n'est pas garantie par une contrainte en base ; en l'absence de la table
 * cible, aucune validation applicative d'existence n'est possible aujourd'hui.
 * Elle sera ajoutée dans le service correspondant dès que {@code Etablissement}
 * existera (T-05), suivant le même principe que pour une référence
 * polymorphe {@code personneType}/{@code personneId} : vérifier l'existence de
 * la cible dans le service, avant insertion, puisque la base ne peut pas le
 * faire à sa place.</p>
 */
@Entity
@Table(name = "affectations_etablissement")
public class AffectationEtablissement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "etablissement_id", nullable = false)
    private UUID etablissementId;

    /**
     * Rôles cumulés sur cette affectation. {@link RoleCode} est une énumération
     * (correction T-04, lot 1 n°2) : plus de jointure vers une table
     * {@code roles}, une simple table de collection {@code affectation_roles}
     * (colonne {@code role_code}) suffit — sans coût de lecture, résolu en
     * mémoire par {@link tg.novadigital.edukeys.identite.security.PermissionResolver}.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "affectation_roles", joinColumns = @JoinColumn(name = "affectation_id"))
    @Column(name = "role_code", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);

    protected AffectationEtablissement() {
    }

    public AffectationEtablissement(Utilisateur utilisateur, UUID etablissementId, Set<RoleCode> roles) {
        this.utilisateur = utilisateur;
        this.etablissementId = etablissementId;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(RoleCode.class) : EnumSet.copyOf(roles);
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public UUID getEtablissementId() {
        return etablissementId;
    }

    public Set<RoleCode> getRoles() {
        return roles;
    }
}
