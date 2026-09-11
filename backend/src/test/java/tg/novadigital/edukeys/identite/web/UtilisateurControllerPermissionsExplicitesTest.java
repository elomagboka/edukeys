package tg.novadigital.edukeys.identite.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * CLAUDE.md, règle 11 : tout endpoint de données métier est gardé par une
 * permission explicite ({@code hasAuthority(...)}), jamais {@code isAuthenticated()}
 * ni le défaut {@code anyRequest().authenticated()} — à l'exception des deux
 * endpoints qui portent sur l'appelant lui-même, jamais sur les données d'un
 * tiers ({@code GET /moi}, {@code POST /moi/mot-de-passe}).
 *
 * <p>Test de réflexion plutôt qu'une lecture manuelle du contrôleur en revue :
 * un futur endpoint ajouté sans {@code hasAuthority(...)} explicite (ex. par
 * copier-coller d'un {@code isAuthenticated()} existant) fait échouer ce test
 * immédiatement.</p>
 */
class UtilisateurControllerPermissionsExplicitesTest {

    private static final Set<String> METHODES_EXEMPTEES = Set.of("moi", "changerMonMotDePasse");

    @Test
    void chaqueEndpointDeDonneesMetier_estGardeParUnePermissionExplicite() {
        for (Method methode : UtilisateurController.class.getDeclaredMethods()) {
            PreAuthorize annotation = methode.getAnnotation(PreAuthorize.class);
            if (annotation == null) {
                continue;
            }
            if (METHODES_EXEMPTEES.contains(methode.getName())) {
                assertThat(annotation.value())
                        .withFailMessage("%s doit rester isAuthenticated() : c'est un endpoint sur l'appelant lui-même.",
                                methode.getName())
                        .isEqualTo("isAuthenticated()");
                continue;
            }
            assertThat(annotation.value())
                    .withFailMessage(
                            "%s doit être gardé par hasAuthority('...') explicite (CLAUDE.md, règle 11), "
                                    + "pas par %s.",
                            methode.getName(), annotation.value())
                    .startsWith("hasAuthority(");
        }
    }

    @Test
    void aucuneMethodePubliqueDuControleur_nEchappeAUneAnnotationDeSecurite() {
        long methodesSansAnnotation = Arrays.stream(UtilisateurController.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getAnnotation(PreAuthorize.class) == null)
                .count();

        assertThat(methodesSansAnnotation)
                .withFailMessage("Chaque endpoint public de UtilisateurController doit porter @PreAuthorize.")
                .isZero();
    }
}
