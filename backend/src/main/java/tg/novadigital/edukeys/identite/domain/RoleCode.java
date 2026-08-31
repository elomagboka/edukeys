package tg.novadigital.edukeys.identite.domain;

import java.util.EnumSet;
import java.util.Set;

import static tg.novadigital.edukeys.identite.domain.Permission.BULLETIN_CONSULTER;
import static tg.novadigital.edukeys.identite.domain.Permission.DEVOIR_CREER;
import static tg.novadigital.edukeys.identite.domain.Permission.ENFANT_CONSULTER;
import static tg.novadigital.edukeys.identite.domain.Permission.ETABLISSEMENT_CREER;
import static tg.novadigital.edukeys.identite.domain.Permission.ETABLISSEMENT_GERER;
import static tg.novadigital.edukeys.identite.domain.Permission.NOTE_SAISIR;
import static tg.novadigital.edukeys.identite.domain.Permission.UTILISATEUR_GERER;
import static tg.novadigital.edukeys.identite.domain.Permission.UTILISATEUR_GERER_PLATEFORME;

/**
 * Catalogue fermé des rôles d'Edukeys, chacun portant ses permissions
 * effectives en mémoire (correction T-04, lot 1 n°2). {@link Role} et
 * {@link Permission} étaient des entités JPA avant cette correction : la
 * résolution des permissions d'une affectation déclenchait une requête (et un
 * N+1) à chaque appel authentifié, alors que ce référentiel ne varie jamais
 * sans déploiement. {@link tg.novadigital.edukeys.identite.security.PermissionResolver}
 * fait désormais l'union de ces ensembles en mémoire, sans aucun accès base.
 */
public enum RoleCode {
    // SUPER_ADMIN ne porte pas UTILISATEUR_GERER : cette permission est
    // celle d'ADMIN sur SON établissement, alors que SUPER_ADMIN n'accède
    // jamais aux données d'un établissement (ADR-0002 §5). La confusion des
    // deux a causé une fuite inter-établissement (relecture T-04, repasse 2).
    // SUPER_ADMIN ne porte plus ETABLISSEMENT_GERER non plus (durcissement
    // T-10, 2e revue) : cette permission garde aussi SiteController et
    // LogoController, l'organisation métier interne d'un établissement
    // (ADR-0005) — SUPER_ADMIN n'agit que sur l'existence des établissements
    // (ETABLISSEMENT_CREER) et les comptes de plateforme
    // (UTILISATEUR_GERER_PLATEFORME), jamais sur les sites ou le logo d'un
    // établissement client.
    SUPER_ADMIN(ETABLISSEMENT_CREER, UTILISATEUR_GERER_PLATEFORME),
    ADMIN(ETABLISSEMENT_GERER, UTILISATEUR_GERER),
    DIRECTION(),
    GESTIONNAIRE(),
    ENSEIGNANT(NOTE_SAISIR, DEVOIR_CREER),
    PARENT(ENFANT_CONSULTER, BULLETIN_CONSULTER),
    ELEVE();

    private final Set<Permission> permissions;

    RoleCode(Permission... permissions) {
        this.permissions = permissions.length == 0
                ? Set.of()
                : EnumSet.copyOf(Set.of(permissions));
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
