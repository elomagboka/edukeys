package tg.novadigital.edukeys.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link Clock} système, injectable partout où « aujourd'hui » entre dans une
 * règle métier (ex. R7, US-05 : période académique « en cours »), plutôt que
 * {@code LocalDate.now()} en dur — un service qui dépend d'un {@code Clock}
 * injecté se teste avec {@code Clock.fixed(...)}, sans dépendre de la date
 * réelle d'exécution des tests.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
