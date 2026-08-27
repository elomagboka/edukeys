package tg.novadigital.edukeys.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exporte le contrat OpenAPI dans {@code target/openapi.json} à chaque build
 * (T-07) : c'est ce fichier, pas un serveur qui tourne, qui alimente
 * {@code npm run api:generate} côté frontend (T-11) — un contrat qui change
 * sans que ce test tourne ne serait détecté qu'au build frontend, trop tard.
 *
 * <p>Approche MockMvc plutôt que {@code springdoc-openapi-maven-plugin}
 * (qui démarre un serveur réel) : plus simple, pas de port à ouvrir en CI, et
 * {@code /v3/api-docs} reste accessible en profil {@code test} (voir
 * {@code SecurityConfig}) précisément pour ce test.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exporteLeContratOpenApi() throws Exception {
        String contrat = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(contrat).contains("\"openapi\"");

        Path cible = Path.of("target", "openapi.json");
        ecrire(cible, contrat);
    }

    private void ecrire(Path cible, String contenu) throws IOException {
        Files.createDirectories(cible.getParent());
        Files.writeString(cible, contenu);
    }
}
