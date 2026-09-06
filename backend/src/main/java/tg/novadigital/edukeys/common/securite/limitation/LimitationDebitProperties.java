package tg.novadigital.edukeys.common.securite.limitation;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration externalisée de la limitation de débit sur les endpoints
 * sensibles (issue #58) : deux compteurs indépendants, l'un par compte
 * (identifiant soumis dans la requête, pas un utilisateur résolu — voir
 * {@link FiltreLimitationDebit}), l'autre par adresse IP.
 *
 * <p>Valeurs par défaut pensées pour le contexte togolais (voir
 * {@code docs/adr}, aucun ADR dédié à ce jour — justification en commentaire
 * ici faute d'ADR séparé) : les opérateurs mobiles mutualisent leurs adresses
 * publiques, une école ou un cybercafé entier peut sortir sur une seule IP.
 * Un seuil par IP serré bloquerait tous les parents d'un même opérateur à
 * cause d'un seul maladroit — d'où un seuil par IP volontairement large
 * (50 échecs de tolérance) contre un seuil par compte serré (3 échecs de
 * tolérance), qui protège une cible désignée sans gêner le voisinage
 * réseau.</p>
 *
 * <p>{@code cheminsProteges} rend l'ajout d'un endpoint trivial (US-04
 * activation/réinitialisation de mot de passe, US-06 pré-inscription
 * publique) : aucun {@code if} en dur dans le filtre, seulement une entrée de
 * configuration.</p>
 */
@Component
@ConfigurationProperties(prefix = "edukeys.securite.limitation-debit")
public class LimitationDebitProperties {

    /** Chemins protégés, comparés à {@code HttpServletRequest.getRequestURI()} (correspondance exacte). */
    private List<String> cheminsProteges = List.of("/api/v1/auth/login", "/api/v1/auth/refresh");

    private final Compteur parCompte = new Compteur(3, Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    // Seuil large : voir la javadoc de la classe pour la justification du contexte togolais.
    private final Compteur parIp = new Compteur(50, Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    /** Taille maximale du corps de requête mis en cache pour en extraire l'identifiant (voir {@code RequeteAvecCorpsMisEnCache}). */
    private int tailleMaxCorpsOctets = 4096;

    /** Borne du nombre de clés distinctes suivies par chaque compteur (protection contre l'épuisement mémoire, voir {@code CompteurAttenteCroissante}). */
    private long tailleMaxCache = 100_000;

    public List<String> getCheminsProteges() {
        return cheminsProteges;
    }

    public void setCheminsProteges(List<String> cheminsProteges) {
        this.cheminsProteges = cheminsProteges;
    }

    public Compteur getParCompte() {
        return parCompte;
    }

    public Compteur getParIp() {
        return parIp;
    }

    public int getTailleMaxCorpsOctets() {
        return tailleMaxCorpsOctets;
    }

    public void setTailleMaxCorpsOctets(int tailleMaxCorpsOctets) {
        this.tailleMaxCorpsOctets = tailleMaxCorpsOctets;
    }

    public long getTailleMaxCache() {
        return tailleMaxCache;
    }

    public void setTailleMaxCache(long tailleMaxCache) {
        this.tailleMaxCache = tailleMaxCache;
    }

    /**
     * Paramètres d'un compteur à attente croissante : {@code seuilTolerance}
     * échecs sont acceptés sans délai, puis chaque échec supplémentaire
     * double le délai d'attente ({@code delaiInitial}, {@code delaiInitial*2},
     * {@code delaiInitial*4}...) jusqu'à {@code delaiPlafond}. L'état d'une
     * clé expire après {@code dureeExpiration} d'inactivité — c'est ce qui
     * borne la durée de vie d'une entrée dans le cache Caffeine sous-jacent,
     * en complément de la borne de taille.
     */
    public static class Compteur {

        private int seuilTolerance;
        private Duration delaiInitial;
        private Duration delaiPlafond;
        private Duration dureeExpiration;

        public Compteur() {
        }

        public Compteur(int seuilTolerance, Duration delaiInitial, Duration delaiPlafond, Duration dureeExpiration) {
            this.seuilTolerance = seuilTolerance;
            this.delaiInitial = delaiInitial;
            this.delaiPlafond = delaiPlafond;
            this.dureeExpiration = dureeExpiration;
        }

        public int getSeuilTolerance() {
            return seuilTolerance;
        }

        public void setSeuilTolerance(int seuilTolerance) {
            this.seuilTolerance = seuilTolerance;
        }

        public Duration getDelaiInitial() {
            return delaiInitial;
        }

        public void setDelaiInitial(Duration delaiInitial) {
            this.delaiInitial = delaiInitial;
        }

        public Duration getDelaiPlafond() {
            return delaiPlafond;
        }

        public void setDelaiPlafond(Duration delaiPlafond) {
            this.delaiPlafond = delaiPlafond;
        }

        public Duration getDureeExpiration() {
            return dureeExpiration;
        }

        public void setDureeExpiration(Duration dureeExpiration) {
            this.dureeExpiration = dureeExpiration;
        }
    }
}
