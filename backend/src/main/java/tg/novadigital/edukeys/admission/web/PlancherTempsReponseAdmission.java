package tg.novadigital.edukeys.admission.web;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import jakarta.annotation.PreDestroy;
import tg.novadigital.edukeys.admission.AdmissionProperties;

/**
 * Égalise le temps de réponse de la soumission publique, quel que soit le
 * chemin emprunté (I4, 3e revue US-06).
 *
 * <p>La réponse est déjà strictement identique — toujours 201, référence et
 * message générique — mais son <em>délai</em> ne l'était pas : un dossier
 * déjà existant est renvoyé sans insertion du dossier ni des pièces, donc
 * nettement plus vite. Un tiers qui connaît le nom, les prénoms et la date de
 * naissance d'un enfant pouvait ainsi chronométrer la réponse pour savoir
 * s'il a déjà postulé dans cette école. C'est le même défaut que le temps de
 * réponse d'{@code /auth/login} (T-04) : là-bas, on paie toujours le coût
 * BCrypt même sans compte à comparer. Ici, le coût manquant est une écriture
 * en base ; on ne peut pas l'exécuter pour de faux sans écrire réellement, et
 * une écriture jetée rendrait le chemin idempotent aussi coûteux que la
 * création. On borne donc le temps par le bas : toute réponse de cet endpoint
 * part au plus tôt au bout du plancher.</p>
 *
 * <p><strong>Asynchrone depuis la 3e revue (point 4)</strong> : {@link
 * #executerAvecPlancherAsync} exécute {@code traitement} de façon
 * <em>synchrone</em>, sur le thread appelant (le thread servlet) — seule
 * l'<em>attente</em> restante jusqu'au plancher est reportée sur {@link
 * #planificateur}, un {@link ScheduledExecutorService} borné et dédié, aux
 * threads nommés. Le thread servlet est donc rendu à Tomcat pendant cette
 * attente au lieu d'être immobilisé par {@code Thread.sleep} : avec 1,2 s de
 * plancher et un pool partagé avec {@code /auth} et le back-office, un
 * {@code sleep} bloquant figeait l'application entière dès ~170 requêtes par
 * seconde sur cette seule route non authentifiée. {@code PorteeEtablissement},
 * le contexte de sécurité et {@code ContexteEtablissement} ne sont jamais
 * concernés par ce report : ils sont portés par des variables de thread
 * ouvertes et fermées entièrement à l'intérieur de l'exécution synchrone de
 * {@code traitement}, jamais pendant l'attente elle-même.</p>
 *
 * <p><strong>Ce que couvre réellement le plancher</strong> (corrigé, 3e
 * revue, point 8) : uniquement ce qui s'exécute à l'intérieur de {@code
 * traitement}, c'est-à-dire {@code DemandeAdmissionService#soumettrePublique}
 * — création, doublon (I4) et tout refus métier qu'il lève ({@code
 * RegleMetierViolee}, {@code RessourceIntrouvableException}, formats de
 * pièces...). Il ne couvre PAS {@code @Valid} (erreurs de validation Bean
 * Validation), les erreurs de désassemblage multipart, le 413 (pièce trop
 * volumineuse au niveau conteneur) ni le 422 Turnstile / 429 débit : ces
 * chemins sont tous levés avant d'atteindre ce contrôleur (filtres ou
 * résolveur d'arguments Spring MVC). C'est sans risque : contrairement au cas
 * central (un dossier existe déjà ou non), aucun de ces refus ne dépend de
 * données déjà présentes en base pour cet enfant précis — ils ne dépendent que
 * de ce que l'appelant a lui-même envoyé, rien de nouveau n'est donc
 * observable en les chronométrant.</p>
 *
 * <p>Le plancher doit rester supérieur au chemin le plus lent en conditions
 * normales, sinon l'écart réapparaît au-dessus de lui. {@code 0} le
 * désactive.</p>
 */
@Component
public class PlancherTempsReponseAdmission {

    private static final Logger LOG = LoggerFactory.getLogger(PlancherTempsReponseAdmission.class);

    private final AdmissionProperties proprietes;
    private final ScheduledExecutorService planificateur;

    public PlancherTempsReponseAdmission(AdmissionProperties proprietes) {
        this.proprietes = proprietes;
        this.planificateur = Executors.newScheduledThreadPool(2, new NommageThreadPlancher());
    }

    @PreDestroy
    public void arreterLePlanificateur() {
        planificateur.shutdown();
    }

    /**
     * Exécute {@code traitement} de façon synchrone (sur le thread appelant),
     * puis retourne un {@link DeferredResult} qui ne se résout — valeur de
     * retour comme exception — qu'au bout du plancher, sans jamais bloquer le
     * thread appelant pendant cette attente.
     */
    public <T> DeferredResult<T> executerAvecPlancherAsync(Supplier<T> traitement) {
        long debutNanos = System.nanoTime();
        T valeur = null;
        RuntimeException erreur = null;
        try {
            valeur = traitement.get();
        } catch (RuntimeException e) {
            erreur = e;
        }

        DeferredResult<T> resultat = new DeferredResult<>();
        programmerLivraison(resultat, valeur, erreur, debutNanos);
        return resultat;
    }

    private <T> void programmerLivraison(DeferredResult<T> resultat, T valeur, RuntimeException erreur, long debutNanos) {
        Duration plancher = proprietes.getPlancherTempsReponse();
        Runnable livraison = () -> {
            if (erreur != null) {
                resultat.setErrorResult(erreur);
            } else {
                resultat.setResult(valeur);
            }
        };

        if (plancher == null || plancher.isZero() || plancher.isNegative()) {
            livraison.run();
            return;
        }

        long resteMillis = plancher.toMillis() - Duration.ofNanos(System.nanoTime() - debutNanos).toMillis();
        if (resteMillis <= 0) {
            // Le traitement a dépassé le plancher : l'écart entre chemins
            // redevient observable, le plancher est donc sous-dimensionné.
            LOG.debug("plancher_temps_reponse_depasse plancher_ms={}", plancher.toMillis());
            livraison.run();
            return;
        }
        planificateur.schedule(livraison, resteMillis, TimeUnit.MILLISECONDS);
    }

    /** Threads nommés et démons : ne doivent jamais empêcher l'arrêt propre de l'application. */
    private static final class NommageThreadPlancher implements ThreadFactory {
        private final AtomicInteger compteur = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "admission-plancher-" + compteur.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
