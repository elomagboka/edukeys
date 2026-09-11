package tg.novadigital.edukeys.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Exporte le contrat OpenAPI à chaque build (T-07) : c'est ce fichier, pas un
 * serveur qui tourne, qui alimente {@code npm run api:generate} côté frontend
 * (T-11).
 *
 * <p><strong>Le contrat est versionné</strong> dans {@code docs/api/openapi.json},
 * pas seulement écrit dans {@code target/}. Deux raisons : régénérer les types
 * frontend ne demande alors ni Docker ni build backend (le contrat est déjà
 * dans le dépôt), et surtout tout changement d'API devient <em>relisable en
 * diff de pull request</em> — aujourd'hui, un endpoint qui change ou
 * disparaît n'apparaît nulle part pour un relecteur.</p>
 *
 * <p><strong>Un contrat versionné qui dérive du code est pire que pas de
 * contrat</strong> : on croit lire l'API et on lit son passé. La CI régénère
 * donc le fichier et refuse la PR s'il diffère de la version committée, avec
 * le même mécanisme que pour les types générés du frontend (voir
 * {@code .github/workflows/ci.yml}).</p>
 *
 * <p>Approche MockMvc plutôt que {@code springdoc-openapi-maven-plugin} (qui
 * démarre un serveur réel) : plus simple, pas de port à ouvrir en CI, et
 * {@code /v3/api-docs} reste accessible en profil {@code test} (voir
 * {@code SecurityConfig}) précisément pour ce test.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiExportTest {

    /**
     * Contrat versionné, relatif au module {@code backend/} d'où tourne
     * Maven. C'est la référence : {@code frontend/scripts/generate-api.mjs}
     * lit ce fichier par défaut.
     */
    private static final Path CONTRAT_VERSIONNE = Path.of("..", "docs", "api", "openapi.json");

    /**
     * Préfixes des contrôleurs de démonstration et de test, exclus du contrat
     * par {@code springdoc.paths-to-exclude} (voir {@code application.yml}).
     * Ils n'existent dans aucun environnement réel.
     */
    private static final List<String> PREFIXES_INTERNES = List.of("/internal", "/test-exceptions");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exporteLeContratOpenApi() throws Exception {
        String contrat = telechargerContrat();

        assertThat(contrat).contains("\"openapi\"");

        ecrire(CONTRAT_VERSIONNE, mettreEnForme(contrat));
    }

    /**
     * Indenté et à clés triées, pour que le fichier versionné serve
     * réellement à quelque chose en relecture.
     *
     * <p>L'indentation rend le diff lisible : sur une seule ligne, un
     * endpoint ajouté apparaîtrait comme une ligne entière modifiée. Le tri
     * des clés, lui, rend le fichier <em>déterministe</em> : SpringDoc
     * sérialise les chemins dans l'ordre de découverte des contrôleurs, le
     * même ordre instable qui produisait les {@code operationId} suffixés.
     * Sans tri, deux builds du même code pourraient produire deux fichiers
     * différents et faire échouer la vérification de dérive de la CI sans
     * qu'aucune API n'ait changé.</p>
     */
    private String mettreEnForme(String contrat) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        Object arbre = mapper.readValue(contrat, Object.class);
        return mapper.writer(imprimeurALineFeed()).writeValueAsString(arbre) + "\n";
    }

    /**
     * Indenteur à saut de ligne {@code \n} explicite, et non
     * {@link System#lineSeparator()}.
     *
     * <p>C'est indispensable, pas cosmétique : l'indenteur par défaut de
     * Jackson utilise le séparateur du système. Un développeur sous Windows
     * committerait un fichier en CRLF, le runner Linux le régénérerait en LF,
     * et la vérification de dérive de la CI échouerait à chaque PR sans
     * qu'aucune API n'ait changé — l'environnement de développement est
     * Windows en local et Linux en conteneur (CLAUDE.md).</p>
     */
    private DefaultPrettyPrinter imprimeurALineFeed() {
        DefaultIndenter indenteur = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter imprimeur = new DefaultPrettyPrinter();
        imprimeur.indentObjectsWith(indenteur);
        imprimeur.indentArraysWith(indenteur);
        return imprimeur;
    }

    /**
     * Garde-fou sur {@code springdoc.paths-to-exclude} : une clé mal
     * orthographiée (ou déplacée par une refonte de {@code application.yml})
     * est ignorée silencieusement par Spring Boot, et les chemins internes
     * reviendraient dans le contrat sans que rien ne le signale. Ce test
     * vérifie l'effet, pas la présence de la propriété.
     */
    @Test
    void leContratNExposeAucunCheminInterneNiDeTest() throws Exception {
        List<String> cheminsInternes = cheminsDuContrat(telechargerContrat()).stream()
                .filter(chemin -> PREFIXES_INTERNES.stream().anyMatch(chemin::startsWith))
                .toList();

        assertThat(cheminsInternes)
                .as("""
                        Ces chemins n'existent dans aucun environnement réel : les exposer ferait \
                        générer au frontend des types pour des endpoints qu'il ne peut pas appeler. \
                        Vérifier springdoc.paths-to-exclude dans application.yml.""")
                .isEmpty();
    }

    private String telechargerContrat() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private List<String> cheminsDuContrat(String contrat) throws IOException {
        JsonNode chemins = new ObjectMapper().readTree(contrat).path("paths");
        List<String> resultat = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = chemins.fields(); it.hasNext();) {
            resultat.add(it.next().getKey());
        }
        return resultat;
    }

    private void ecrire(Path cible, String contenu) throws IOException {
        Files.createDirectories(cible.getParent());
        Files.writeString(cible, contenu);
    }
}
