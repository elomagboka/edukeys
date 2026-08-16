package tg.novadigital.edukeys.common.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auteur d'audit renvoyé "system" faute de contexte de sécurité : Spring
 * Security n'est câblé qu'en T-04. Remplacer par une lecture du contexte de
 * sécurité à ce moment-là.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    private static final String AUTEUR_SYSTEME = "system";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(AUTEUR_SYSTEME);
    }
}
