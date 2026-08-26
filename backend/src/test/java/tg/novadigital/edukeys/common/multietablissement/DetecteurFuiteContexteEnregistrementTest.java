package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Preuve en conditions réelles (T-05, revue post-livraison, point 2) que
 * {@link DetecteurFuiteContexteFilter} est réellement enregistré dans la
 * chaîne servlet Spring Boot, avec un ordre qui l'englobe correctement.
 *
 * <p><b>Pourquoi ce test existe en plus de {@code FuiteContexteEtablissementTest}</b> :
 * ce dernier instancie le filtre directement
 * ({@code new DetecteurFuiteContexteFilter(...)}) et appelle
 * {@code doFilter(...)} à la main — il prouve le comportement de la classe,
 * jamais qu'elle est effectivement tissée dans la chaîne servlet de
 * l'application. Un filtre déclaré {@code @Component} mais jamais réellement
 * enregistré (mauvais profil, {@code @Order} qui ne compile plus après un
 * refactor, bean exclu par une configuration de test) laisserait passer
 * toute requête sans qu'aucun de ces tests directs ne s'en aperçoive —
 * exactement le défaut déjà corrigé pour {@code GardeContexteEtablissement}
 * dans {@code IsolationEtablissementTest} (a).</p>
 *
 * <p>La preuve passe par une vraie requête HTTP via {@link MockMvc} — donc à
 * travers la chaîne de filtres servlet réellement configurée par Spring Boot,
 * pas un appel direct sur le filtre — vers
 * {@code DemoFuiteContexteController} (profil {@code test} uniquement), qui
 * ouvre un contexte d'établissement et ne le referme jamais.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DetecteurFuiteContexteEnregistrementTest {

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void nettoyerContexteResiduel() {
        // Filet de sécurité si le détecteur ne purgeait pas correctement :
        // sans lui, une fuite de ce test contaminerait les tests suivants du
        // même run, exactement l'incident déjà documenté au JOURNAL pour
        // ContexteEtablissement.entityManagerFactory.
        ContexteEtablissement.purger();
    }

    @Test
    @DisplayName("le detecteur intercepte, en conditions HTTP reelles, une requete qui rend la main avec un contexte ouvert")
    void detecteurEstTisseDansLaChaineServletReelle() {
        // L'exception levée par le détecteur (profil test) traverse la chaîne
        // de filtres en dehors du DispatcherServlet : MockMvc.perform() la
        // propage donc telle quelle (pas enveloppée dans une ServletException,
        // le filtre la relève lui-même hors de tout bloc try/catch d'un autre
        // filtre), la preuve recherchée est précisément qu'elle est levée.
        assertThatThrownBy(() -> mockMvc.perform(get("/internal/demo/fuite-contexte")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Contexte d'établissement encore ouvert");

        // Preuve secondaire : si l'aspect qui purge n'était pas non plus
        // tissé, le contexte resterait posé après la requête.
        assertThat(ContexteEtablissement.courant()).isEmpty();
    }
}
