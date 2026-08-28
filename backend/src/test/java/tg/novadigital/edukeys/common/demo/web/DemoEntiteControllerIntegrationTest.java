package tg.novadigital.edukeys.common.demo.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tg.novadigital.edukeys.common.demo.domain.DemoEntite;
import tg.novadigital.edukeys.common.demo.repository.DemoEntiteRepository;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.identite.security.UtilisateurPrincipal;

/**
 * Test d'intégration bout-en-bout du contrôleur de démonstration T-03 :
 * prouve que la pagination, le tri et le filtrage dynamiques fonctionnent
 * réellement au travers de la pile JPA/PostgreSQL, pas seulement en mémoire.
 *
 * <p>La base est un conteneur PostgreSQL éphémère fourni par
 * {@link tg.novadigital.edukeys.testsupport.ConfigurationBaseDeTest} et migré
 * par Flyway au démarrage du contexte : seul Docker est requis, ni
 * {@code docker compose} ni base {@code edukeys_test} préexistante.</p>
 *
 * <p><b>T-05, sous-tâche 10</b> : {@link DemoEntite} est passée sous
 * {@code EntiteEtablissement}, donc désormais soumise au filtre Hibernate et
 * à {@code GardeContexteEtablissement}. Le contrôleur de démonstration est
 * exposé sans authentification ({@code /internal/**} est {@code permitAll}),
 * donc {@code ContexteEtablissementFilter} n'ouvre aucun contexte pour ces
 * requêtes : ce test en ouvre un explicitement, sur le thread de test — le
 * même thread que celui qui exécute {@code MockMvc.perform(...)} en mode
 * {@code MOCK} (aucun dispatch asynchrone ici), donc le contexte ouvert ici
 * est bien vu par le contrôleur et le repository lors du peuplement des
 * données ({@code @BeforeEach}, hors HTTP).</p>
 *
 * <p>Pour les appels {@code MockMvc}, en revanche, un contexte ouvert par le
 * test <em>avant</em> {@code mockMvc.perform(...)} et refermé après ne
 * convient pas : {@code DetecteurFuiteContexteFilter} (T-05) fait échouer
 * toute requête qui rend la main avec un contexte encore ouvert, y compris un
 * contexte ouvert par autre chose que {@code ContexteEtablissementFilter}
 * lui-même. La requête simule donc une authentification
 * ({@link UtilisateurPrincipal}, sans jeton) via
 * {@code SecurityMockMvcRequestPostProcessors.authentication(...)} :
 * {@code ContexteEtablissementFilter} — déjà enregistré dans la chaîne de
 * sécurité, y compris pour {@code /internal/**} — ouvre et referme alors
 * lui-même le contexte pour la durée exacte de la requête, exactement comme
 * en production.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoEntiteControllerIntegrationTest {

    private static final UUID ETABLISSEMENT_DE_TEST = UUID.randomUUID();
    private static final UUID AUTRE_ETABLISSEMENT = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoEntiteRepository demoEntiteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void peuplerJeuDeDonnees() {
        viderLaTable();
        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_DE_TEST)) {
            demoEntiteRepository.save(new DemoEntite("Alpha", "sciences", 1));
            demoEntiteRepository.save(new DemoEntite("Beta", "lettres", 2));
            demoEntiteRepository.save(new DemoEntite("Gamma", "sciences", 3));
            for (int i = 0; i < 25; i++) {
                demoEntiteRepository.save(new DemoEntite("Serie-" + i, "sciences", i));
            }
        }
    }

    @AfterEach
    void nettoyer() {
        viderLaTable();
    }

    /**
     * Vide la table de démo directement via JDBC, hors du repository
     * applicatif : {@link DemoEntiteRepository} n'expose aucune méthode
     * {@code delete*} (suppression physique interdite, cf. CLAUDE.md).
     */
    private void viderLaTable() {
        jdbcTemplate.execute("TRUNCATE TABLE demo_entites");
    }

    /**
     * Simule l'authentification lue par {@code ContexteEtablissementFilter} :
     * c'est lui, et lui seul, qui doit ouvrir/refermer le contexte pour la
     * requête, avec le même établissement que celui utilisé pour peupler les
     * données de test.
     */
    private ResultActions dansUnContexte(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder) throws Exception {
        var principal = new UtilisateurPrincipal(UUID.randomUUID(), ETABLISSEMENT_DE_TEST, Set.of());
        var authentification = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return mockMvc.perform(requestBuilder.with(authentication(authentification)));
    }

    @Test
    void listeUnePremierePageDeTailleParDefaut_quandAucunParametreDePagination() throws Exception {
        dansUnContexte(get("/internal/demo/entites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.taille").value(20))
                .andExpect(jsonPath("$.contenu.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(28));
    }

    @Test
    void listeLaDeuxiemePage_quandPageDemandee() throws Exception {
        dansUnContexte(get("/internal/demo/entites").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.contenu.length()").value(8))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void trieParLibelleDescendant_quandSortDemande() throws Exception {
        dansUnContexte(get("/internal/demo/entites")
                        .param("size", "3")
                        .param("sort", "libelle,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].libelle").value("Serie-9"))
                .andExpect(jsonPath("$.contenu[1].libelle").value("Serie-8"));
    }

    @Test
    void filtreParLibelleEtCategorie_quandLesDeuxCriteresSontFournis() throws Exception {
        dansUnContexte(get("/internal/demo/entites")
                        .param("libelle", "alph")
                        .param("categorie", "sciences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].libelle").value("Alpha"));
    }

    @Test
    void neRenvoieAucunResultat_quandLaCategorieNeCorrespondAAucuneEntite() throws Exception {
        dansUnContexte(get("/internal/demo/entites").param("categorie", "inexistante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.contenu.length()").value(0));
    }

    @Test
    void plafonneLaTailleDePageA100_quandTailleDemandeeDepasseLeMaximum() throws Exception {
        dansUnContexte(get("/internal/demo/entites").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taille").value(100));
    }

    /**
     * Critère de fin de T-06 : modifier une entité crée une révision
     * consultable, avec l'auteur et la date. {@code DemoEntite} n'exposant
     * aucun mutateur métier (seul {@code desactiver()} en hérite via
     * {@code EntiteDesactivable}), c'est la désactivation logique qui sert de
     * modification ici — une écriture réelle, pas un artifice de test.
     */
    @Test
    void creeUneRevisionConsultableAvecAuteurEtDate_quandUneEntiteEstModifiee() throws Exception {
        UUID auteurId = UUID.randomUUID();
        UUID entiteId = creerPuisDesactiverEnTantQue(auteurId);

        dansUnContexte(get("/internal/demo/entites/" + entiteId + "/historique"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].typeRevision").value("AJOUT"))
                .andExpect(jsonPath("$[0].actif").value(true))
                .andExpect(jsonPath("$[0].auteur").value(auteurId.toString()))
                .andExpect(jsonPath("$[0].date").exists())
                .andExpect(jsonPath("$[1].typeRevision").value("MODIFICATION"))
                .andExpect(jsonPath("$[1].actif").value(false))
                .andExpect(jsonPath("$[1].auteur").value(auteurId.toString()))
                .andExpect(jsonPath("$[1].date").exists());
    }

    /**
     * Angle mort T-05 comblé en T-06 (voir JOURNAL) : les tables {@code _aud}
     * d'Envers échappent au filtre Hibernate. {@link
     * tg.novadigital.edukeys.common.audit.HistoriqueService} doit donc
     * refuser explicitement l'historique d'une entité d'un autre
     * établissement, pas seulement s'appuyer sur le filtre absent ici.
     */
    @Test
    void refuseLHistorique_quandLEntiteAppartientAUnAutreEtablissement() throws Exception {
        UUID entiteId = creerPuisDesactiverEnTantQue(UUID.randomUUID());

        var principalAutreEtablissement = new UtilisateurPrincipal(UUID.randomUUID(), AUTRE_ETABLISSEMENT, Set.of());
        var authentification = new UsernamePasswordAuthenticationToken(principalAutreEtablissement, null, List.of());

        mockMvc.perform(get("/internal/demo/entites/" + entiteId + "/historique").with(authentication(authentification)))
                .andExpect(status().isNotFound());
    }

    private UUID creerPuisDesactiverEnTantQue(UUID auteurId) {
        var principal = new UtilisateurPrincipal(auteurId, ETABLISSEMENT_DE_TEST, Set.of());
        var authentification = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentification);
        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_DE_TEST)) {
            DemoEntite entite = demoEntiteRepository.save(new DemoEntite("Historique", "test", 1));
            entite.desactiver();
            demoEntiteRepository.save(entite);
            return entite.getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
