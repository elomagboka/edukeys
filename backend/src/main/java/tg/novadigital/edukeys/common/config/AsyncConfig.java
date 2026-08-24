package tg.novadigital.edukeys.common.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissementTaskDecorator;

/**
 * Active {@code @Async} et branche {@link ContexteEtablissementTaskDecorator}
 * sur l'exécuteur par défaut (sous-tâche 9, T-05) : sans ce décorateur,
 * chaque méthode {@code @Async} démarrée pendant une requête HTTP perdrait le
 * contexte d'établissement ouvert par {@code ContexteEtablissementFilter}
 * (le {@code ThreadLocal} de {@code ContexteEtablissement} n'est pas hérité,
 * voir sa javadoc).
 *
 * <p>Aucun traitement {@code @Async} n'existe encore dans le projet à T-05 :
 * ce bean pose le socle pour les premiers à venir (US-25, US-28).</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private final ContexteEtablissementTaskDecorator taskDecorator;

    public AsyncConfig(ContexteEtablissementTaskDecorator taskDecorator) {
        this.taskDecorator = taskDecorator;
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("edukeys-async-");
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }
}
