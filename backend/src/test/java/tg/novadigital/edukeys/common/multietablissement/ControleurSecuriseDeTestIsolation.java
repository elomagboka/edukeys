package tg.novadigital.edukeys.common.multietablissement;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tg.novadigital.edukeys.common.demo.repository.DemoEntiteRepository;

/**
 * Contrôleur exclusivement destiné à la sous-tâche 11 de T-05
 * ({@code IsolationEtablissementTest}, cas C7). N'existe que dans les
 * sources de test — même précédent qu'{@code ExceptionDeDemoControleur}.
 *
 * <p>Aucun endpoint métier réel n'existe encore au Sprint 0 pour prouver la
 * règle 11 de CLAUDE.md (« tout endpoint de données métier est gardé par une
 * permission explicite ») : ce contrôleur en simule un, gardé par
 * {@code @PreAuthorize(hasAuthority(...))} sur une permission métier
 * ordinaire ({@code NOTE_SAISIR}), qu'aucun {@code SUPER_ADMIN} ne porte
 * (ADR-0002 §5 ; RoleCode.SUPER_ADMIN ne porte que ETABLISSEMENT_CREER et
 * UTILISATEUR_GERER_PLATEFORME, deux permissions de plateforme, jamais une
 * permission métier).</p>
 */
@Profile("test")
@RestController
@RequestMapping("/internal/isolation/demo-entites")
public class ControleurSecuriseDeTestIsolation {

    private final DemoEntiteRepository demoEntiteRepository;

    public ControleurSecuriseDeTestIsolation(DemoEntiteRepository demoEntiteRepository) {
        this.demoEntiteRepository = demoEntiteRepository;
    }

    @PreAuthorize("hasAuthority('NOTE_SAISIR')")
    @GetMapping("/nombre")
    public long compter() {
        return demoEntiteRepository.count();
    }
}
