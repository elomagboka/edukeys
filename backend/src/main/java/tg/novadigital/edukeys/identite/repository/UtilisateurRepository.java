package tg.novadigital.edukeys.identite.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.identite.domain.RoleCode;
import tg.novadigital.edukeys.identite.domain.Utilisateur;

public interface UtilisateurRepository extends BaseRepository<Utilisateur> {

    Optional<Utilisateur> findByIdentifiantConnexionAndActifTrue(String identifiantConnexionNormalise);

    boolean existsByIdentifiantConnexionAndActifTrue(String identifiantConnexionNormalise);

    Optional<Utilisateur> findByEmailAndActifTrue(String email);

    boolean existsByEmailAndActifTrue(String email);

    /**
     * Comptes actifs affectés à un établissement donné (sous-tâche 12-13,
     * T-05), via une affectation elle-même active — un compte désactivé côté
     * {@code AffectationEtablissement} ne doit plus apparaître dans la liste
     * d'un ADMIN, même si le compte {@code Utilisateur} reste actif ailleurs.
     * JPQL explicite plutôt qu'une méthode dérivée : {@code Utilisateur}
     * n'étend pas {@code EntiteEtablissement} (ADR-0002, précision
     * d'implémentation T-05 — lu pendant le login, avant tout contexte), donc
     * aucun filtre Hibernate ne le borne automatiquement ; le cloisonnement
     * repose entièrement sur cette jointure explicite vers
     * {@code AffectationEtablissement}, jamais sur une requête native (CLAUDE.md,
     * règle 2 : les requêtes natives sur une entité métier échapperaient de
     * toute façon au filtre — ici sans objet, mais la même prudence
     * s'applique par cohérence).
     */
    @Query("""
            select distinct u from Utilisateur u
            join u.affectations a
            where a.etablissementId = :etablissementId
              and a.actif = true
              and u.actif = true
            """)
    Page<Utilisateur> findParEtablissementCourantActif(@Param("etablissementId") UUID etablissementId, Pageable pageable);

    /**
     * Liste plateforme (SUPER_ADMIN) hors comptes élèves/parents (US-08a) : un
     * compte est exclu s'il porte au moins un des rôles {@code rolesDeCompte}
     * (ELEVE, PARENT) ET aucun autre rôle sur aucune de ses affectations
     * <em>actives</em> — un enseignant qui est aussi parent reste listé, un ancien
     * enseignant devenu seulement parent ne l'est plus. Une seule requête (deux
     * sous-requêtes {@code exists}), donc pas de N+1 et un comptage de
     * pagination exact ; un compte sans affectation (SUPER_ADMIN) reste listé.
     * {@code AffectationEtablissement} n'étant pas une entité filtrée par
     * établissement, la sous-requête voit bien toutes les affectations.
     */
    @Query("""
            select u from Utilisateur u
            where not (
                exists (select 1 from AffectationEtablissement a join a.roles r
                        where a.utilisateur = u and r in :rolesDeCompte)
                and not exists (select 1 from AffectationEtablissement a2 join a2.roles r2
                        where a2.utilisateur = u and a2.actif = true and r2 not in :rolesDeCompte)
            )
            """)
    Page<Utilisateur> findTousSaufComptesDeRoles(@Param("rolesDeCompte") Collection<RoleCode> rolesDeCompte, Pageable pageable);
}
