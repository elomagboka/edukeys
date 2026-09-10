package tg.novadigital.edukeys.common.securite.reseau;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Ordonne la chaîne des filtres qui dépendent des en-têtes {@code
 * X-Forwarded-*}.
 *
 * <p>Boot enregistre son {@link ForwardedHeaderFilter} sur
 * {@link Ordered#HIGHEST_PRECEDENCE} exactement, et ce filtre supprime les
 * en-têtes qu'il consomme. Aucun filtre applicatif ne peut donc s'exécuter
 * avant lui ni lire {@code X-Forwarded-For} — or {@link FiltreAdresseIpCliente}
 * en a besoin pour déterminer l'IP cliente sans faire confiance aux éléments
 * forgeables par l'appelant (issue #58). On réenregistre donc explicitement le
 * filtre de Boot un cran plus loin, ce qui libère la première place.</p>
 *
 * <p>{@code ForwardedHeaderFilter} continue de faire son travail habituel —
 * réécriture du schéma, de l'hôte et du port pour la génération d'URL
 * absolues (en-têtes {@code Location}, liens OpenAPI). Seule la
 * détermination de l'IP cliente lui échappe désormais.</p>
 */
@Configuration
public class ConfigurationEnTetesTransferes {

    @Bean
    public FilterRegistrationBean<FiltreAdresseIpCliente> enregistrementFiltreAdresseIpCliente(
            @Value("${edukeys.securite.reseau.nb-proxys-de-confiance}") int nbProxysDeConfiance) {
        FilterRegistrationBean<FiltreAdresseIpCliente> enregistrement =
                new FilterRegistrationBean<>(new FiltreAdresseIpCliente(nbProxysDeConfiance));
        enregistrement.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return enregistrement;
    }

    /**
     * Réenregistrement explicite du {@code ForwardedHeaderFilter} de Boot, à
     * un ordre postérieur à {@link FiltreAdresseIpCliente}. Remplace
     * l'enregistrement automatique déclenché par
     * {@code server.forward-headers-strategy: framework}.
     */
    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> enregistrementForwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> enregistrement =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        enregistrement.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return enregistrement;
    }
}
