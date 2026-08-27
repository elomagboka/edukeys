package tg.novadigital.edukeys.common.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;

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
}
