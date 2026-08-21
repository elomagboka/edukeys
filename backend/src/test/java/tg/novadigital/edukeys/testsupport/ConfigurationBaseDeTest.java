package tg.novadigital.edukeys.testsupport;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Socle de base de données des tests d'intégration (T-08, avancé) : un
 * conteneur PostgreSQL éphémère démarré par Testcontainers, à la place de la
 * base {@code localhost:5432} montée par {@code docker compose}. Le runner
 * GitHub n'a pas cette base, mais il a Docker : la CI redevient reproductible
 * sans que les tests existants changent d'une ligne.
 *
 * <p>L'image {@code postgres:18-alpine} suit exactement la version de
 * production (PostgreSQL 18 managé sur Render) : un test qui passe ici ne doit
 * pas buter en production sur une différence de moteur, notamment sur les
 * index partiels et les types utilisés par les migrations Flyway.</p>
 *
 * <p>Cette classe est déclarée comme auto-configuration <b>du classpath de
 * test uniquement</b> (voir
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}).
 * C'est ce qui permet à chaque {@code @SpringBootTest} d'en bénéficier sans
 * l'importer : aucune modification des tests d'intégration existants. Le
 * conteneur suit le cycle de vie du contexte Spring, mis en cache entre les
 * classes de test — un seul conteneur pour toute la campagne, et non un par
 * classe. Le {@code before = ServiceConnectionAutoConfiguration.class} n'est
 * pas décoratif : c'est cette auto-configuration qui traduit
 * {@code @ServiceConnection} en propriétés de connexion, et elle ne voit que
 * les définitions de bean déjà enregistrées quand elle s'exécute.</p>
 *
 * <p>{@code @ServiceConnection} publie l'URL, l'utilisateur et le mot de passe
 * réels du conteneur vers la DataSource <i>et</i> vers Flyway ; c'est pourquoi
 * plus aucune URL n'est écrite en dur dans {@code application-test.yml}.</p>
 */
@AutoConfiguration(before = ServiceConnectionAutoConfiguration.class)
public class ConfigurationBaseDeTest {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
                .withDatabaseName("edukeys_test")
                .withUsername("edukeys")
                .withPassword("edukeys");
    }
}
