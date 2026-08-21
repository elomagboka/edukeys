package tg.novadigital.edukeys.common.demo.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tg.novadigital.edukeys.common.demo.domain.DemoEntite;
import tg.novadigital.edukeys.common.demo.repository.DemoEntiteRepository;

/**
 * Test d'intégration bout-en-bout du contrôleur de démonstration T-03 :
 * prouve que la pagination, le tri et le filtrage dynamiques fonctionnent
 * réellement au travers de la pile JPA/PostgreSQL, pas seulement en mémoire.
 *
 * <p>La base est un conteneur PostgreSQL éphémère fourni par
 * {@link tg.novadigital.edukeys.testsupport.ConfigurationBaseDeTest} et migré
 * par Flyway au démarrage du contexte : seul Docker est requis, ni
 * {@code docker compose} ni base {@code edukeys_test} préexistante.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoEntiteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoEntiteRepository demoEntiteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void peuplerJeuDeDonnees() {
        viderLaTable();
        demoEntiteRepository.save(new DemoEntite("Alpha", "sciences", 1));
        demoEntiteRepository.save(new DemoEntite("Beta", "lettres", 2));
        demoEntiteRepository.save(new DemoEntite("Gamma", "sciences", 3));
        for (int i = 0; i < 25; i++) {
            demoEntiteRepository.save(new DemoEntite("Serie-" + i, "sciences", i));
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

    @Test
    void listeUnePremierePageDeTailleParDefaut_quandAucunParametreDePagination() throws Exception {
        mockMvc.perform(get("/internal/demo/entites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.taille").value(20))
                .andExpect(jsonPath("$.contenu.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(28));
    }

    @Test
    void listeLaDeuxiemePage_quandPageDemandee() throws Exception {
        mockMvc.perform(get("/internal/demo/entites").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.contenu.length()").value(8))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void trieParLibelleDescendant_quandSortDemande() throws Exception {
        mockMvc.perform(get("/internal/demo/entites")
                        .param("size", "3")
                        .param("sort", "libelle,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].libelle").value("Serie-9"))
                .andExpect(jsonPath("$.contenu[1].libelle").value("Serie-8"));
    }

    @Test
    void filtreParLibelleEtCategorie_quandLesDeuxCriteresSontFournis() throws Exception {
        mockMvc.perform(get("/internal/demo/entites")
                        .param("libelle", "alph")
                        .param("categorie", "sciences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].libelle").value("Alpha"));
    }

    @Test
    void neRenvoieAucunResultat_quandLaCategorieNeCorrespondAAucuneEntite() throws Exception {
        mockMvc.perform(get("/internal/demo/entites").param("categorie", "inexistante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.contenu.length()").value(0));
    }

    @Test
    void plafonneLaTailleDePageA100_quandTailleDemandeeDepasseLeMaximum() throws Exception {
        mockMvc.perform(get("/internal/demo/entites").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taille").value(100));
    }
}
