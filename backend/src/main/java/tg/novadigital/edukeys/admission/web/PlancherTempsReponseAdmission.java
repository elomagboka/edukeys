package tg.novadigital.edukeys.admission.web;

import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
 * part au plus tôt au bout du plancher, création, doublon et refus de
 * validation confondus.</p>
 *
 * <p>Le plancher doit rester supérieur au chemin le plus lent en conditions
 * normales, sinon l'écart réapparaît au-dessus de lui. Il n'immobilise un
 * thread que sur cette seule route, déjà protégée par Turnstile et par la
 * limitation de débit ; {@code 0} le désactive.</p>
 */
@Component
public class PlancherTempsReponseAdmission {

    private static final Logger LOG = LoggerFactory.getLogger(PlancherTempsReponseAdmission.class);

    private final AdmissionProperties proprietes;

    public PlancherTempsReponseAdmission(AdmissionProperties proprietes) {
        this.proprietes = proprietes;
    }

    /**
     * Exécute {@code traitement} et retarde la sortie — valeur de retour
     * comme exception — jusqu'au plancher.
     */
    public <T> T executerAvecPlancher(Supplier<T> traitement) {
        long debut = System.nanoTime();
        try {
            return traitement.get();
        } finally {
            attendreLePlancher(debut);
        }
    }

    private void attendreLePlancher(long debutNanos) {
        Duration plancher = proprietes.getPlancherTempsReponse();
        if (plancher == null || plancher.isZero() || plancher.isNegative()) {
            return;
        }
        long resteMillis = plancher.toMillis() - Duration.ofNanos(System.nanoTime() - debutNanos).toMillis();
        if (resteMillis <= 0) {
            // Le traitement a dépassé le plancher : l'écart entre chemins
            // redevient observable, le plancher est donc sous-dimensionné.
            LOG.debug("plancher_temps_reponse_depasse plancher_ms={}", plancher.toMillis());
            return;
        }
        try {
            Thread.sleep(resteMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
