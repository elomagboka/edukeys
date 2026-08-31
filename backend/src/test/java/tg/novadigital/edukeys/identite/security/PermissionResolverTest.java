package tg.novadigital.edukeys.identite.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.identite.domain.Permission;

/**
 * Prouve l'arbitrage T-04 n°1 (union des permissions des rôles cumulés sur
 * une affectation) au niveau où elle est réellement calculée :
 * {@link PermissionResolver}, consommé par {@link JwtAuthenticationFilter} à
 * chaque requête. Depuis la correction T-04 (lot 1 n°2), cette résolution est
 * purement en mémoire — {@link tg.novadigital.edukeys.identite.domain.RoleCode}
 * porte directement ses permissions, aucun repository n'est plus impliqué.
 */
class PermissionResolverTest {

    private final PermissionResolver resolver = new PermissionResolver();

    @Test
    void renvoieLUnionDesPermissions_quandDeuxRolesDistinctsSurLaMemeAffectation() {
        Set<String> permissions = resolver.resoudrePermissions(Set.of("ENSEIGNANT", "PARENT"));

        // La permission propre à PARENT seul doit apparaître : une union mal
        // calculée (ex. seul le premier rôle résolu, ou une intersection) ne
        // la contiendrait pas.
        assertThat(permissions).containsExactlyInAnyOrder(
                "NOTE_SAISIR", "DEVOIR_CREER", "ENFANT_CONSULTER", "BULLETIN_CONSULTER");
    }

    @Test
    void neDedoublonnePasAPerte_quandUnePermissionEstPartageeParDeuxRoles() {
        Set<String> permissions = resolver.resoudrePermissions(Set.of("ADMIN", "SUPER_ADMIN"));

        // ETABLISSEMENT_GERER (ADMIN seul depuis le durcissement T-10, 2e
        // revue — SUPER_ADMIN ne la porte plus) et ETABLISSEMENT_CREER
        // (SUPER_ADMIN seul) doivent toutes deux apparaître dans l'union :
        // une union mal calculée (ex. seul le premier rôle résolu) en
        // perdrait une. UTILISATEUR_GERER (ADMIN, borné à son établissement)
        // et UTILISATEUR_GERER_PLATEFORME (SUPER_ADMIN, tous établissements)
        // restent délibérément distinctes depuis la relecture T-04 qui a
        // montré qu'une permission partagée entre les deux rôles fuitait
        // entre établissements.
        assertThat(permissions).containsExactlyInAnyOrder(
                "ETABLISSEMENT_CREER", "ETABLISSEMENT_GERER", "UTILISATEUR_GERER", "UTILISATEUR_GERER_PLATEFORME");
    }

    @Test
    void neRenvoieAucunePermission_quandAucunCodeDeRoleFourni() {
        Set<String> permissions = resolver.resoudrePermissions((Set<String>) null);

        assertThat(permissions).isEmpty();
    }

    @Test
    void neRenvoieAucunePermission_quandRoleSansPermissionAttribuee() {
        Set<String> permissions = resolver.resoudrePermissions(Set.of("DIRECTION", "GESTIONNAIRE", "ELEVE"));

        assertThat(permissions).isEmpty();
    }

    /**
     * ADR-0002 §5 : SUPER_ADMIN « n'a aucun accès aux données métier ». La
     * garantie qu'y décrit l'ADR (le filtre Hibernate multi-établissement) est
     * le périmètre de T-05, pas encore construit — voir
     * {@code docs/adr/0002-multi-etablissement.md}, ligne 95, et
     * {@code docs/SPRINT-0.md}, T-05. Tant qu'il n'existe pas, ce test est LA
     * seule garantie effective côté code : que SUPER_ADMIN, même après bascule
     * sur un établissement, ne reçoit jamais une permission métier — quel que
     * soit le futur endpoint qui la vérifierait via {@code hasAuthority(...)}.
     * Il ne prouve PAS l'isolation des données elle-même : un endpoint gardé
     * uniquement par {@code isAuthenticated()} laisserait SUPER_ADMIN passer.
     */
    @Test
    void neDonneJamaisDePermissionMetierASuperAdmin_ceQuiEstLaSeuleGarantieDeLADR0002TantQueLeFiltreHibernateNexistePas() {
        // ETABLISSEMENT_CREER et UTILISATEUR_GERER_PLATEFORME ne sont pas des
        // permissions métier : elles portent l'administration de la
        // plateforme elle-même (créer un établissement, gérer les comptes de
        // tous les établissements), qui est précisément la mission de
        // SUPER_ADMIN selon ADR-0002. La règle CLAUDE.md 11 (« aucune
        // permission métier ») vise les permissions sur les données d'un
        // établissement — notes, élèves, finances — pas celles-là. D'où leur
        // exclusion explicite ci-dessous plutôt qu'un ensemble vide.
        // ETABLISSEMENT_GERER n'est PLUS ici (durcissement T-10, 2e revue) :
        // elle garde aussi SiteController et LogoController, l'organisation
        // métier interne d'un établissement (ADR-0005), que SUPER_ADMIN ne
        // doit jamais pouvoir modifier pour un établissement client.
        // UTILISATEUR_GERER (sans le suffixe) n'est PAS ici non plus : c'est
        // la permission d'ADMIN, bornée à son propre établissement —
        // SUPER_ADMIN ne la porte plus depuis que les deux ont été
        // distinguées (relecture T-04, repasse n°2 : les confondre laissait
        // ADMIN lire les comptes de tous les établissements).
        Set<String> permissionsDePlateforme = Set.of("ETABLISSEMENT_CREER", "UTILISATEUR_GERER_PLATEFORME");
        Set<String> toutesLesPermissionsConnues = Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        Set<String> permissionsMetierConnues = new HashSet<>(toutesLesPermissionsConnues);
        permissionsMetierConnues.removeAll(permissionsDePlateforme);

        Set<String> permissionsSuperAdmin = resolver.resoudrePermissions(Set.of("SUPER_ADMIN"));

        assertThat(permissionsSuperAdmin).isEqualTo(permissionsDePlateforme);
        // Vérification indépendante de la liste ci-dessus : toute permission
        // métier ajoutée un jour à l'enum Permission fait échouer ce test si
        // elle est accidentellement attribuée à SUPER_ADMIN dans RoleCode.
        assertThat(permissionsSuperAdmin).doesNotContainAnyElementsOf(permissionsMetierConnues);
    }
}
