package tg.novadigital.edukeys.common.config;

import java.util.Arrays;
import java.util.Map;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import tg.novadigital.edukeys.common.exception.CodeErreur;

/**
 * Description globale de l'API (T-07) : schéma de sécurité JWT déclaré une
 * seule fois ici (bouton <em>Authorize</em> de Swagger UI), appliqué par
 * défaut à tout endpoint documenté. Les deux endpoints publics
 * ({@code /auth/login}, {@code /auth/refresh}) annulent ce défaut
 * explicitement avec {@code @SecurityRequirements} vide sur leur méthode.
 *
 * <p>Swagger UI n'est exposé qu'en profil {@code local} (voir
 * {@code SecurityConfig}) : accessible à un développeur sur son poste,
 * jamais en recette ni en production. Le JSON {@code /v3/api-docs} reste
 * atteignable en profil {@code test} — c'est lui qui alimente
 * {@code npm run api:generate} côté frontend (T-11) et
 * {@code OpenApiExportTest}, qui l'écrit sur disque au build.</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Edukeys — API",
                version = "v1",
                description = "API de gestion scolaire Edukeys, éditée par Nova Digital (Togo)."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER)
public class OpenApiConfig {

    /** Nom du schéma référencé par toute réponse d'erreur (voir {@link #problemDetailEdukeys()}). */
    static final String SCHEMA_PROBLEM_DETAIL = "ProblemDetailEdukeys";

    /**
     * Schéma RFC 7807 enrichi (DELTA 3, US-01) : {@code code} y est typé en
     * énumération dérivée de {@link CodeErreur} — pas en {@code string} libre,
     * sinon le frontend ne gagne rien à la génération de types — et
     * {@code correlationId} y figure, en plus des quatre champs RFC 7807
     * standard. C'est ce schéma que référence
     * {@link #reponsesDErreurTypeesGlobalement()} sur toute réponse d'erreur
     * de l'API, sans que chaque contrôleur n'ait à l'annoter un par un.
     */
    @Bean
    public Schema<?> problemDetailEdukeys() {
        StringSchema codeSchema = new StringSchema();
        codeSchema.setEnum(Arrays.stream(CodeErreur.values()).map(Enum::name).toList());

        Schema<Object> schema = new Schema<>();
        schema.setName(SCHEMA_PROBLEM_DETAIL);
        schema.setType("object");
        schema.addProperty("type", new StringSchema().example("about:blank"));
        schema.addProperty("title", new StringSchema());
        schema.addProperty("status", new Schema<>().type("integer").format("int32"));
        schema.addProperty("detail", new StringSchema());
        schema.addProperty("instance", new StringSchema());
        schema.addProperty("correlationId", new StringSchema());
        schema.addProperty("code", codeSchema);
        return schema;
    }

    /**
     * Référence {@value #SCHEMA_PROBLEM_DETAIL} sur toute réponse d'erreur
     * (code HTTP {@code >= 400}) de l'API, posée globalement par
     * {@link GlobalOpenApiCustomizer} plutôt que sur chaque
     * {@code @ApiResponse} : sans cela, {@code code} et
     * {@code correlationId} restent documentés côté backend
     * ({@code GestionnaireExceptionsGlobal}) mais jamais livrés au
     * consommateur — exactement la lacune signalée en revue de l'US-01. Les
     * autres modules en bénéficient sans retouche : aucune annotation
     * supplémentaire à poser contrôleur par contrôleur.
     */
    @Bean
    public GlobalOpenApiCustomizer reponsesDErreurTypeesGlobalement() {
        return openApi -> {
            openApi.getComponents().addSchemas(SCHEMA_PROBLEM_DETAIL, problemDetailEdukeys());

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }
                for (Map.Entry<String, ApiResponse> entree : operation.getResponses().entrySet()) {
                    if (!estCodeErreur(entree.getKey())) {
                        continue;
                    }
                    ApiResponse reponse = entree.getValue();
                    if (reponse.getContent() != null && !reponse.getContent().isEmpty()) {
                        continue;
                    }
                    reponse.setContent(contenuProblemDetail());
                }
            }));
        };
    }

    private static boolean estCodeErreur(String statut) {
        try {
            return Integer.parseInt(statut) >= 400;
        } catch (NumberFormatException e) {
            return false; // "default" ou tout autre libellé non numérique.
        }
    }

    private static Content contenuProblemDetail() {
        Schema<Object> reference = new Schema<>();
        reference.set$ref("#/components/schemas/" + SCHEMA_PROBLEM_DETAIL);
        return new Content().addMediaType(
                "application/problem+json",
                new MediaType().schema(reference));
    }
}
