package tg.novadigital.edukeys.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import tg.novadigital.edukeys.common.repository.BaseRepositoryImpl;

/**
 * Branche {@link BaseRepositoryImpl} comme implémentation de base de tous les
 * repositories Spring Data du projet (sous-tâche 8, T-05) : sans ce
 * {@code repositoryBaseClass} explicite, Spring Boot instancierait
 * {@code SimpleJpaRepository} par défaut, dont {@code findById} passe par
 * {@code EntityManager.find()} — l'angle mort A1 de l'ADR-0002 (le filtre
 * Hibernate est ignoré par un chargement direct par identifiant).
 *
 * <p>Déclarer explicitement {@code @EnableJpaRepositories} fait reculer
 * {@code JpaRepositoriesAutoConfiguration} (Spring Boot ne s'auto-configure
 * que si aucune configuration utilisateur n'existe) : le
 * {@code basePackages} doit donc couvrir tout le projet, comme le ferait
 * l'auto-configuration par défaut à partir de {@code EdukeysApplication}.</p>
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "tg.novadigital.edukeys",
        repositoryBaseClass = BaseRepositoryImpl.class)
public class JpaRepositoriesConfig {
}
