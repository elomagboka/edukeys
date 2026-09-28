package tg.novadigital.edukeys.common.securite.limitation;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Budget de succès par clé et par fenêtre glissante (3e revue, point 3) :
 * contrairement à {@link CompteurAttenteCroissante}, qui ne compte que les
 * échecs, ce compteur borne le nombre de <strong>succès</strong> — utile là où
 * un jeton anti-robot valide (Turnstile) ne suffit pas à empêcher un dépôt
 * illimité de dossiers coûteux (jusqu'à 15 Mo de pièces jointes chacun, US-06).
 *
 * <p>Instance unique, mémoire locale au processus — même limite qu'indiqué
 * dans la javadoc de {@link CompteurAttenteCroissante} au passage à plusieurs
 * instances Render.</p>
 */
public class CompteurBudgetJournalier {

    private final int budget;
    private final Cache<String, AtomicInteger> cache;

    public CompteurBudgetJournalier(int budget, Duration fenetre, long tailleMaxCache) {
        this.budget = budget;
        this.cache = Caffeine.newBuilder()
                .maximumSize(tailleMaxCache)
                .expireAfterWrite(fenetre)
                .build();
    }

    /** Nombre de succès déjà comptés pour cette clé dans la fenêtre courante. */
    public int nombreDeSucces(String cle) {
        AtomicInteger compteur = cache.getIfPresent(cle);
        return compteur == null ? 0 : compteur.get();
    }

    /** {@code true} si un nouveau succès resterait dans le budget (appelé avant traitement, jamais après). */
    public boolean budgetDisponible(String cle) {
        return nombreDeSucces(cle) < budget;
    }

    /** Enregistre un succès — appelé uniquement après un traitement effectivement réussi. */
    public void enregistrerSucces(String cle) {
        cache.asMap().computeIfAbsent(cle, ignoreCle -> new AtomicInteger(0)).incrementAndGet();
    }

    /** Réservé aux tests : repart d'un état vierge sans redémarrer le contexte Spring. */
    public void reinitialiserTout() {
        cache.invalidateAll();
    }
}
