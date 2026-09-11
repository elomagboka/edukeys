package tg.novadigital.edukeys.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SUPER_ADMIN est un rôle de plateforme (ADR-0002 §5), jamais un rôle
 * métier : il ne doit porter aucune permission d'établissement, seulement
 * les deux permissions de plateforme. Une régression ici a déjà causé une
 * fuite inter-établissement (voir la javadoc de {@link Permission#UTILISATEUR_GERER_PLATEFORME}).
 */
class RoleCodeTest {

    @Test
    void superAdmin_nePorteQueLesPermissionsDePlateforme() {
        assertThat(RoleCode.SUPER_ADMIN.getPermissions())
                .containsExactlyInAnyOrder(Permission.ETABLISSEMENT_CREER, Permission.UTILISATEUR_GERER_PLATEFORME);
    }

    @Test
    void superAdmin_nePorteNiUtilisateurConsulterNiRoleAttribuer() {
        assertThat(RoleCode.SUPER_ADMIN.getPermissions())
                .doesNotContain(Permission.UTILISATEUR_CONSULTER, Permission.ROLE_ATTRIBUER,
                        Permission.UTILISATEUR_GERER, Permission.ETABLISSEMENT_GERER);
    }

    @Test
    void admin_porteUtilisateurGererEtRoleAttribuer_lesDeuxDistinctement() {
        assertThat(RoleCode.ADMIN.getPermissions())
                .contains(Permission.UTILISATEUR_GERER, Permission.ROLE_ATTRIBUER, Permission.UTILISATEUR_CONSULTER);
        assertThat(RoleCode.ADMIN.getPermissions()).doesNotContain(Permission.UTILISATEUR_GERER_PLATEFORME);
    }

    @Test
    void direction_nePorteQueUtilisateurConsulter_parmiLesPermissionsDeGestionDeComptes() {
        assertThat(RoleCode.DIRECTION.getPermissions()).containsExactly(Permission.UTILISATEUR_CONSULTER);
    }
}
