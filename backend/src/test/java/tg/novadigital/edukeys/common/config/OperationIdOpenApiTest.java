package tg.novadigital.edukeys.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verrouille la stabilité des {@code operationId} du contrat OpenAPI, dont
 * dépend le code généré côté frontend ({@code npm run api:generate}, T-11).
 *
 * <p><strong>Le problème que ce test empêche de revenir.</strong> Sans
 * {@code @Operation(operationId = "...")}, SpringDoc dérive l'identifiant du
 * nom de la méthode Java et, en cas d'homonymie entre contrôleurs, ajoute un
 * suffixe numérique attribué dans l'<em>ordre de découverte</em> des
 * contrôleurs : le contrat exposait ainsi {@code lister_1}, {@code lister_2},
 * {@code lister_3}, {@code creer_1}, {@code creer_2}, {@code desactiver_2}.
 * Cette numérotation n'est pas une propriété de l'API : ajouter n'importe où
 * une méthode {@code lister()} décale tous les suffixes suivants et casse le
 * code frontend alors qu'aucune fonctionnalité n'a changé. Ces noms ne
 * disent rien non plus de ce que l'opération fait.</p>
 *
 * <p>Un nouvel endpoint sans {@code operationId} explicite réintroduirait le
 * problème silencieusement — d'où ce test, qui échoue au build plutôt qu'au
 * prochain sprint frontend.</p>
 *
 * <p>Aucune liste d'exclusion : <em>tous</em> les endpoints du contrat sont
 * nommés explicitement, y compris ceux des contrôleurs de démonstration et de
 * test. Une liste d'exclusion aurait fini par contenir l'endpoint qu'on
 * voulait vérifier.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationIdOpenApiTest {

    /**
     * Suffixe ajouté par SpringDoc en cas d'homonymie de méthode. C'est bien
     * la fin de l'identifiant qui est vérifiée : un {@code operationId}
     * légitime peut contenir un chiffre ailleurs (une version de ressource,
     * par exemple).
     */
    private static final Pattern SUFFIXE_NUMERIQUE_SPRINGDOC = Pattern.compile(".*_\\d+$");

    /** Verbes HTTP portant une opération dans un objet {@code pathItem} OpenAPI. */
    private static final Set<String> VERBES_HTTP =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("aucun operationId du contrat ne porte de suffixe numérique attribué par SpringDoc")
    void aucunOperationIdNePorteDeSuffixeNumerique() throws Exception {
        List<Operation> operations = chargerOperations();

        List<String> instables = operations.stream()
                .filter(operation -> SUFFIXE_NUMERIQUE_SPRINGDOC.matcher(operation.operationId()).matches())
                .map(Operation::description)
                .toList();

        assertThat(instables)
                .as("""
                        operationId dérivé du nom de méthode Java, donc instable : le suffixe numérique \
                        dépend de l'ordre de découverte des contrôleurs et se décalera au prochain \
                        endpoint homonyme, cassant le code frontend généré. Ajouter \
                        @Operation(operationId = "...") nommé d'après la ressource (creerEtablissement, \
                        listerSites, ...) sur chacun de ces endpoints.""")
                .isEmpty();
    }

    /**
     * Deuxième mode de panne, distinct du premier : un {@code operationId}
     * explicite recopié par étourderie d'un endpoint à l'autre. Le contrat
     * reste alors « stable » au sens du test précédent, mais le générateur
     * frontend fusionne ou écrase deux opérations — panne plus sournoise
     * qu'un simple renommage.
     */
    @Test
    @DisplayName("les operationId sont renseignés et uniques dans tout le contrat")
    void lesOperationIdSontRenseignesEtUniques() throws Exception {
        List<Operation> operations = chargerOperations();
        assertThat(operations).as("contrat OpenAPI sans aucune opération : export cassé").isNotEmpty();

        assertThat(operations)
                .as("operationId absent ou vide : SpringDoc n'a pas pu en dériver un")
                .allSatisfy(operation -> assertThat(operation.operationId()).isNotBlank());

        Map<String, List<String>> parIdentifiant = new TreeMap<>();
        operations.forEach(operation -> parIdentifiant
                .computeIfAbsent(operation.operationId(), identifiant -> new ArrayList<>())
                .add(operation.description()));

        Map<String, List<String>> doublons = new TreeMap<>(parIdentifiant);
        doublons.values().removeIf(endpoints -> endpoints.size() == 1);

        assertThat(doublons)
                .as("operationId partagé par plusieurs endpoints : le générateur frontend en écrasera un")
                .isEmpty();
    }

    /** Une opération du contrat : son identifiant, et de quoi la retrouver dans le code. */
    private record Operation(String operationId, String description) {
    }

    private List<Operation> chargerOperations() throws Exception {
        String contrat = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode chemins = new ObjectMapper().readTree(contrat).path("paths");
        List<Operation> operations = new ArrayList<>();

        for (Iterator<Map.Entry<String, JsonNode>> it = chemins.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> chemin = it.next();
            for (Iterator<Map.Entry<String, JsonNode>> verbes = chemin.getValue().fields(); verbes.hasNext();) {
                Map.Entry<String, JsonNode> verbe = verbes.next();
                if (!VERBES_HTTP.contains(verbe.getKey())) {
                    continue; // parameters, summary, $ref... : pas des opérations.
                }
                operations.add(new Operation(
                        verbe.getValue().path("operationId").asText(""),
                        "%s %s".formatted(verbe.getKey().toUpperCase(), chemin.getKey())));
            }
        }
        return operations;
    }
}
