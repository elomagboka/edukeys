package tg.novadigital.edukeys.common.securite.limitation;

import java.time.Duration;
import java.time.Instant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import tg.novadigital.edukeys.common.securite.limitation.LimitationDebitProperties.Compteur;

/**
 * Compteur d'échecs à attente croissante, adossé à un cache Caffeine borné en
 * taille et en durée de vie. Une clé (empreinte de compte ou adresse IP)
 * accumule des échecs ; au-delà d'un seuil de tolérance, chaque échec
 * supplémentaire double le délai avant la prochaine tentative autorisée,
 * plafonné, jamais un verrouillage définitif — remise à zéro explicite après
 * un succès (voir {@link FiltreLimitationDebit}, seule règle 2 de l'issue
 * #58).
 *
 * <p><strong>Instance unique, mémoire locale au processus.</strong> Ce
 * compteur vit entièrement en mémoire JVM. Au passage à plusieurs instances
 * Render, chaque instance porterait son propre cache Caffeine indépendant :
 * un attaquant réparti sur N instances (par le simple jeu de l'équilibrage de
 * charge) disposerait alors d'un budget d'échecs multiplié par N avant que le
 * compteur le moins chargé ne se déclenche. Un stockage partagé (Redis, avec
 * la même logique d'attente croissante côté client ou via un script Lua
 * atomique) devient alors nécessaire ; ne pas le découvrir en production.</p>
 */
public class CompteurAttenteCroissante {

    private final Compteur parametres;
    private final Cache<String, Etat> cache;

    public CompteurAttenteCroissante(Compteur parametres) {
        this.parametres = parametres;
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(parametres.getDureeExpiration())
                .build();
    }

    public CompteurAttenteCroissante(Compteur parametres, long tailleMaxCache) {
        this.parametres = parametres;
        this.cache = Caffeine.newBuilder()
                .maximumSize(tailleMaxCache)
                .expireAfterWrite(parametres.getDureeExpiration())
                .build();
    }

    /**
     * Durée restante avant que la clé donnée ne soit à nouveau autorisée.
     * {@link Duration#ZERO} (ou négative) signifie qu'aucune attente n'est
     * imposée actuellement.
     */
    public Duration dureeAttenteRestante(String cle) {
        Etat etat = cache.getIfPresent(cle);
        if (etat == null || etat.prochainInstantAutorise == null) {
            return Duration.ZERO;
        }
        Duration restante = Duration.between(Instant.now(), etat.prochainInstantAutorise);
        return restante.isNegative() ? Duration.ZERO : restante;
    }

    /**
     * Enregistre un échec pour la clé donnée. Les {@code seuilTolerance}
     * premiers échecs ne provoquent aucune attente ; chaque échec
     * supplémentaire double le délai précédent (1 s, 2 s, 4 s, 8 s...),
     * plafonné à {@code delaiPlafond}.
     */
    public void enregistrerEchec(String cle) {
        cache.asMap().compute(cle, (ignoreCle, etatActuel) -> {
            int nombreEchecs = (etatActuel == null ? 0 : etatActuel.nombreEchecs) + 1;
            int echecsAuDelaDuSeuil = nombreEchecs - parametres.getSeuilTolerance();

            if (echecsAuDelaDuSeuil <= 0) {
                return new Etat(nombreEchecs, null);
            }

            long facteur = 1L << Math.min(echecsAuDelaDuSeuil - 1, 32);
            Duration delai = parametres.getDelaiInitial().multipliedBy(facteur);
            if (delai.compareTo(parametres.getDelaiPlafond()) > 0) {
                delai = parametres.getDelaiPlafond();
            }
            return new Etat(nombreEchecs, Instant.now().plus(delai));
        });
    }

    /** Remise à zéro après une authentification réussie — jamais de verrouillage définitif (issue #58). */
    public void reinitialiser(String cle) {
        cache.invalidate(cle);
    }

    /** Réservé aux tests : repart d'un état vierge sans redémarrer le contexte Spring. */
    public void reinitialiserTout() {
        cache.invalidateAll();
    }

    private static final class Etat {
        private final int nombreEchecs;
        private final Instant prochainInstantAutorise;

        private Etat(int nombreEchecs, Instant prochainInstantAutorise) {
            this.nombreEchecs = nombreEchecs;
            this.prochainInstantAutorise = prochainInstantAutorise;
        }
    }
}
