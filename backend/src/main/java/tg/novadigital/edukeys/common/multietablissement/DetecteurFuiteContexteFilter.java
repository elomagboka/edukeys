package tg.novadigital.edukeys.common.multietablissement;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Détecte les requêtes qui rendent la main en laissant un contexte
 * d'établissement ouvert, et purge le {@code ThreadLocal} résiduel.
 *
 * <p><b>Actif dans tous les profils</b>, car la fuite ne se produit que sur un
 * pool de threads réellement réutilisés sous charge concurrente — situation
 * qu'aucun test ne reproduit. Un détecteur limité au profil {@code test}
 * surveillerait l'endroit où le risque n'existe pas.</p>
 *
 * <p>Deux comportements, pour un seul et même diagnostic :</p>
 *
 * <ul>
 *   <li><b>profil {@code test}</b> : purge, journalise, puis lève — la fuite
 *       doit rendre le build rouge ;</li>
 *   <li><b>local et production</b> : purge et journalise en {@code ERROR} sans
 *       lever. La requête a déjà abouti ; l'utilisateur n'a pas à voir une
 *       erreur pour un défaut qui ne l'affecte pas, lui. La purge, elle, a lieu
 *       dans les deux cas : c'est elle qui empêche la contamination de la
 *       requête suivante sur ce thread.</li>
 * </ul>
 *
 * <p>Deux fuites échappent au {@code finally} de
 * {@code ContexteEtablissementFilter} :</p>
 *
 * <ul>
 *   <li>une requête <b>non authentifiée</b> ({@code /auth/login},
 *       {@code /auth/refresh}) : le filtre n'ouvre aucune portée, donc son
 *       {@code finally} n'a rien à fermer — si un traitement en ouvre une
 *       malgré tout et l'oublie, rien ne la referme ;</li>
 *   <li>une ouverture qui échoue après avoir posé le {@code ThreadLocal}, cas
 *       rattrapé dans {@code ContexteEtablissement.ouvrir} mais qui doit rester
 *       surveillé.</li>
 * </ul>
 *
 * <p>Placé en tête de chaîne ({@link Ordered#HIGHEST_PRECEDENCE}) pour englober
 * la chaîne Spring Security elle-même, enregistrée à l'ordre -100.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DetecteurFuiteContexteFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DetecteurFuiteContexteFilter.class);

    /** Vrai en profil {@code test} uniquement : la fuite y devient un échec. */
    private final boolean leverEnCasDeFuite;

    @Autowired
    public DetecteurFuiteContexteFilter(Environment environment) {
        this(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("test")));
    }

    DetecteurFuiteContexteFilter(boolean leverEnCasDeFuite) {
        this.leverEnCasDeFuite = leverEnCasDeFuite;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            ContexteEtablissement.purger().ifPresent(restant -> {
                // Jamais d'identifiant d'utilisateur ni de donnée métier ici : un
                // UUID d'établissement et une origine suffisent au diagnostic.
                log.error("fuite_contexte_etablissement uri={} etablissementId={} origine={}",
                        request.getRequestURI(), restant.etablissementId(), restant.origine());
                if (leverEnCasDeFuite) {
                    throw new IllegalStateException(
                            "Contexte d'établissement encore ouvert à la fin de la requête %s (établissement %s, origine %s) : une portée n'a pas été refermée."
                                    .formatted(request.getRequestURI(), restant.etablissementId(), restant.origine()));
                }
            });
        }
    }
}
