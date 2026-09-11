package tg.novadigital.edukeys.identite.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;

public interface AffectationEtablissementRepository extends BaseRepository<AffectationEtablissement> {

    @EntityGraph(attributePaths = "roles")
    List<AffectationEtablissement> findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(UUID utilisateurId);

    boolean existsByUtilisateurIdAndEtablissementIdAndActifTrue(UUID utilisateurId, UUID etablissementId);

    /**
     * Vrai si le compte porte une affectation active sur un établissement
     * <strong>autre</strong> que celui donné (US-04, revue post-implémentation,
     * point A) : borne le rattachement d'une affectation à un compte déjà
     * existant ({@code UtilisateurService#ajouterAffectationCompteExistant})
     * et la régénération d'un mot de passe temporaire
     * ({@code UtilisateurService#regenererMotDePasseTemporaire}) — un ADMIN
     * ne doit jamais pouvoir agir sur un compte qui vit réellement dans un
     * autre établissement.
     */
    boolean existsByUtilisateurIdAndActifTrueAndEtablissementIdNot(UUID utilisateurId, UUID etablissementId);

    /** Toute affectation, active ou non (réactivation, US-04) : ne filtre pas sur {@code actif}. */
    Optional<AffectationEtablissement> findByUtilisateurIdAndEtablissementId(UUID utilisateurId, UUID etablissementId);

    @EntityGraph(attributePaths = {"utilisateur", "roles"})
    Optional<AffectationEtablissement> findByUtilisateurIdAndEtablissementIdAndActifTrue(UUID utilisateurId, UUID etablissementId);

    /**
     * S'il reste au moins une autre affectation active pour ce compte
     * (US-04) : un compte {@code Utilisateur} n'est désactivé que lorsque sa
     * dernière affectation active disparaît.
     */
    boolean existsByUtilisateurIdAndActifTrueAndIdNot(UUID utilisateurId, UUID affectationId);

    /**
     * Identifiants paginés des affectations actives d'un établissement, dont
     * le compte est lui-même actif — sans aucun {@code fetch} de collection
     * (pas de {@code @EntityGraph} ici). Étape 1 du découpage en deux
     * requêtes qui évite le couple pagination + jointure de collection
     * (bascule en pagination mémoire, {@code HHH000104}, CLAUDE.md règle 10) :
     * voir {@link #findByIdIn(List)} pour l'étape 2, qui charge le graphe
     * complet uniquement pour les lignes déjà paginées.
     */
    @Query("""
            select a.id from AffectationEtablissement a
            where a.etablissementId = :etablissementId
              and a.actif = true
              and a.utilisateur.actif = true
            order by a.dateCreation asc
            """)
    Page<UUID> findIdsParEtablissementCourantActif(@Param("etablissementId") UUID etablissementId, Pageable pageable);

    /**
     * Étape 2 du découpage décrit sur {@link #findIdsParEtablissementCourantActif}
     * : charge {@code utilisateur} et {@code roles} en une seule requête pour
     * un lot déjà borné en taille par la pagination — corrige le N+1 constaté
     * (41 requêtes pour une page de 20, CLAUDE.md règle 10).
     */
    @EntityGraph(attributePaths = {"utilisateur", "roles"})
    List<AffectationEtablissement> findByIdIn(List<UUID> ids);

    /**
     * Nombre d'autres affectations actives portant le rôle {@code ADMIN} sur
     * cet établissement (US-04) : un établissement ne doit jamais se
     * retrouver sans aucun ADMIN actif (§3 de la spec — état impossible,
     * jamais un état à réparer après coup).
     */
    @Query("""
            select count(a) from AffectationEtablissement a
            join a.roles r
            where a.etablissementId = :etablissementId
              and a.actif = true
              and a.id <> :affectationId
              and r = tg.novadigital.edukeys.identite.domain.RoleCode.ADMIN
            """)
    long compterAutresAdminsActifs(@Param("etablissementId") UUID etablissementId, @Param("affectationId") UUID affectationId);
}
