package tg.novadigital.edukeys.common.multietablissement;

import java.util.Optional;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Propage explicitement le contexte multi-établissement du thread appelant
 * vers le thread d'exécution {@code @Async} (sous-tâche 9, T-05).
 *
 * <p>{@link ContexteEtablissement} repose sur un {@code ThreadLocal}
 * volontairement <strong>non</strong> {@code InheritableThreadLocal} (voir sa
 * javadoc) : un pool de threads (Tomcat, exécuteur {@code @Async}) ne doit
 * jamais faire fuiter le contexte d'une requête vers la tâche suivante
 * exécutée par le même thread réutilisé. Conséquence directe : sans ce
 * décorateur, une méthode {@code @Async} démarrée <em>pendant</em> une
 * requête HTTP perdrait le contexte ouvert par
 * {@code ContexteEtablissementFilter} — le thread du pool d'exécution
 * asynchrone n'a jamais vu {@code ContexteEtablissement.ouvrir(...)}.</p>
 *
 * <p>Enregistré sur le {@code ThreadPoolTaskExecutor} via
 * {@code AsyncConfig#taskExecutor()}. Rouvre le contexte capturé au moment de
 * la soumission de la tâche (pas au moment de son exécution : le contexte du
 * thread appelant peut déjà avoir été refermé quand la tâche démarre), et le
 * referme systématiquement en {@code finally} — jamais de fuite vers la
 * tâche suivante traitée par ce même thread du pool.</p>
 */
@Component
public class ContexteEtablissementTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Optional<PerimetreEtablissement> perimetreCapture = ContexteEtablissement.courant();

        if (perimetreCapture.isEmpty()) {
            return runnable;
        }

        return () -> {
            PerimetreEtablissement perimetre = perimetreCapture.get();
            try (var portee = ContexteEtablissement.ouvrir(perimetre.etablissementId(), perimetre.origine())) {
                runnable.run();
            }
        };
    }
}
