package tg.novadigital.edukeys.common.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import tg.novadigital.edukeys.common.securite.PrincipalAuditable;

/**
 * Auteur d'audit résolu depuis le contexte de sécurité (T-04 livré), avec
 * repli {@code "system"} pour les traitements sans authentification
 * (migrations, jobs planifiés, tests sans utilisateur simulé). Résolu via
 * {@link PrincipalAuditable} plutôt que via le principal du module
 * {@code identite} directement : {@code common} ne dépend d'aucun module
 * métier (CLAUDE.md, règle 1).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    private static final String AUTEUR_SYSTEME = "system";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof PrincipalAuditable principal) {
                return Optional.of(principal.identifiantAudit());
            }
            return Optional.of(AUTEUR_SYSTEME);
        };
    }
}
