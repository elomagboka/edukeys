package tg.novadigital.edukeys.common.multietablissement;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.hibernate.Session;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Couvre les deux fuites de {@code ThreadLocal} qui échappent au {@code finally}
 * de {@code ContexteEtablissementFilter}. Un thread de pool étant réutilisé
 * entre deux requêtes, une fuite ici se traduirait par un établissement qui
 * déborde sur la requête suivante — sous charge, et sans reproductibilité.
 */
class FuiteContexteEtablissementTest {

    /**
     * {@code ContexteEtablissement.entityManagerFactory} est {@code static} :
     * partagé par tout le run JVM, y compris les classes {@code @SpringBootTest}
     * qui réutilisent un contexte Spring mis en cache (ex.
     * {@code IsolationEtablissementTest}). Ce test le remplace par des mocks
     * pour simuler un échec de ré-armement ; le remettre inconditionnellement à
     * {@code null} en fin de test (au lieu de restaurer la valeur d'origine)
     * a déjà cassé silencieusement le filtre Hibernate d'un test exécuté plus
     * tard dans le même run (T-05, sous-tâche 11 : cause racine de 4 échecs
     * intermittents sur {@code IsolationEtablissementTest}).
     */
    private EntityManagerFactory entityManagerFactoryOriginale;

    @BeforeEach
    void sauvegarderEntityManagerFactory() {
        entityManagerFactoryOriginale = ContexteEtablissement.entityManagerFactoryEnregistree();
    }

    @AfterEach
    void nettoyer() {
        ContexteEtablissement.purger();
        ContexteEtablissement.enregistrerEntityManagerFactory(entityManagerFactoryOriginale);
    }

    @Test
    @DisplayName("ouvrir() ne laisse aucun contexte derrière lui si le ré-armement du filtre échoue")
    void ouvrir_ne_fuite_pas_quand_le_rearmement_echoue() {
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        EntityManager em = mock(EntityManager.class);
        when(em.unwrap(Session.class)).thenThrow(new IllegalStateException("EntityManager fermé"));

        ContexteEtablissement.enregistrerEntityManagerFactory(emf);
        TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(em));
        try {
            assertThatThrownBy(() -> ContexteEtablissement.ouvrir(UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EntityManager fermé");

            // Le point du test : l'appelant n'a reçu aucune PorteeEtablissement à
            // fermer, donc personne ne nettoiera pour lui.
            assertThat(ContexteEtablissement.courant()).isEmpty();
        } finally {
            TransactionSynchronizationManager.unbindResource(emf);
        }
    }

    @Test
    @DisplayName("ouvrir() imbriqué restaure le périmètre précédent si le ré-armement échoue")
    void ouvrir_imbrique_restaure_le_precedent_quand_le_rearmement_echoue() {
        UUID etablissementExterieur = UUID.randomUUID();
        try (var portee = ContexteEtablissement.ouvrir(etablissementExterieur)) {
            EntityManagerFactory emf = mock(EntityManagerFactory.class);
            EntityManager em = mock(EntityManager.class);
            when(em.unwrap(Session.class)).thenThrow(new IllegalStateException("EntityManager fermé"));

            ContexteEtablissement.enregistrerEntityManagerFactory(emf);
            TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(em));
            try {
                assertThatThrownBy(() -> ContexteEtablissement.ouvrir(UUID.randomUUID()))
                        .isInstanceOf(IllegalStateException.class);

                assertThat(ContexteEtablissement.courant())
                        .map(PerimetreEtablissement::etablissementId)
                        .contains(etablissementExterieur);
            } finally {
                TransactionSynchronizationManager.unbindResource(emf);
            }
        }
    }

    @Test
    @DisplayName("le détecteur échoue si une requête rend la main avec un contexte ouvert")
    void detecteur_signale_un_contexte_non_referme() {
        var filtre = new DetecteurFuiteContexteFilter(true);
        var requete = new MockHttpServletRequest("GET", "/api/v1/demo");
        UUID etablissement = UUID.randomUUID();

        assertThatThrownBy(() -> filtre.doFilter(requete, new MockHttpServletResponse(),
                (req, res) -> ContexteEtablissement.ouvrir(etablissement))) // ouverture jamais refermée
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/api/v1/demo")
                .hasMessageContaining(etablissement.toString());
    }

    @Test
    @DisplayName("le détecteur purge le contexte fuité, pour ne pas faire cascader les tests suivants")
    void detecteur_purge_le_contexte_fuite() {
        var filtre = new DetecteurFuiteContexteFilter(true);

        assertThatThrownBy(() -> filtre.doFilter(new MockHttpServletRequest("GET", "/api/v1/demo"),
                new MockHttpServletResponse(),
                (req, res) -> ContexteEtablissement.ouvrir(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ContexteEtablissement.courant()).isEmpty();
    }

    @Test
    @DisplayName("le détecteur laisse passer une requête qui referme correctement sa portée")
    void detecteur_silencieux_quand_tout_est_referme() throws Exception {
        var filtre = new DetecteurFuiteContexteFilter(true);
        var reponse = new MockHttpServletResponse();

        filtre.doFilter(new MockHttpServletRequest("GET", "/api/v1/demo"), reponse, (req, res) -> {
            try (var portee = ContexteEtablissement.ouvrir(UUID.randomUUID())) {
                // traitement normal
            }
        });

        assertThat(ContexteEtablissement.courant()).isEmpty();
    }

    @Test
    @DisplayName("hors profil test, le détecteur purge la fuite sans faire échouer une requête déjà aboutie")
    void detecteur_purge_sans_lever_hors_profil_test() throws Exception {
        var filtre = new DetecteurFuiteContexteFilter(false);
        var reponse = new MockHttpServletResponse();

        filtre.doFilter(new MockHttpServletRequest("GET", "/api/v1/demo"), reponse,
                (req, res) -> ContexteEtablissement.ouvrir(UUID.randomUUID())); // ouverture jamais refermée

        // La réponse de l'utilisateur reste intacte : la fuite est un défaut de
        // programmation, pas une erreur de sa requête.
        assertThat(reponse.getStatus()).isEqualTo(200);
        // La purge, elle, a bien eu lieu — sans quoi le thread suivant hériterait
        // de l'établissement fuité.
        assertThat(ContexteEtablissement.courant()).isEmpty();
    }
}
