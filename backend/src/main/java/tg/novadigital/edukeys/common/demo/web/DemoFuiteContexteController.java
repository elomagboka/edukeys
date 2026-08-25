package tg.novadigital.edukeys.common.demo.web;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;

/**
 * Support de test exclusivement : ouvre un contexte d'établissement et ne le
 * referme jamais, pour prouver en conditions réelles (requête HTTP complète,
 * pas un appel direct sur l'aspect ou le filtre) que
 * {@code DetecteurFuiteContexteFilter} est bien enregistré dans la chaîne
 * servlet Spring Boot avec le bon ordre — même défaut déjà corrigé pour la
 * garde AOP ({@code IsolationEtablissementTest} (a)) : un filtre déclaré
 * comme {@code @Component} mais jamais réellement tissé dans la chaîne
 * échouerait silencieusement, laissant filer une fuite qu'aucun test
 * n'aurait détectée.
 *
 * <p><b>Profil {@code test} uniquement</b> (pas {@code local}, contrairement à
 * {@link DemoEntiteController}) : {@code DetecteurFuiteContexteFilter} ne
 * lève qu'en profil {@code test} ; exposer cet endpoint en local produirait
 * une fuite journalée en {@code ERROR} sans jamais échouer, un piège pour un
 * développeur qui l'appellerait par erreur.</p>
 */
@Profile("test")
@RestController
@RequestMapping("/internal/demo/fuite-contexte")
public class DemoFuiteContexteController {

    @GetMapping
    public void fuiter() {
        ContexteEtablissement.ouvrir(UUID.randomUUID());
        // Volontairement jamais refermée : c'est la fuite que le détecteur
        // doit intercepter avant que la réponse HTTP ne parte.
    }
}
