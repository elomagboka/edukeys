package tg.novadigital.edukeys.common.demo.web;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import tg.novadigital.edukeys.common.audit.HistoriqueService;
import tg.novadigital.edukeys.common.demo.domain.DemoEntite;
import tg.novadigital.edukeys.common.demo.mapper.DemoEntiteMapper;
import tg.novadigital.edukeys.common.demo.repository.DemoEntiteRepository;
import tg.novadigital.edukeys.common.specification.SpecificationsBase;
import tg.novadigital.edukeys.common.web.pagination.PageReponse;
import tg.novadigital.edukeys.common.web.pagination.PaginationUtils;

/**
 * Contrôleur de démonstration du socle de pagination/recherche (T-03) et,
 * depuis T-06, de l'endpoint générique de consultation d'historique
 * ({@link HistoriqueService}). N'est PAS une fonctionnalité métier : sert
 * uniquement de support aux tests d'intégration de ces deux socles. Restreint
 * aux profils de développement et de test.
 */
@Profile({"local", "test"})
@RestController
@RequestMapping("/internal/demo/entites")
public class DemoEntiteController {

    private final DemoEntiteRepository demoEntiteRepository;
    private final HistoriqueService historiqueService;

    public DemoEntiteController(DemoEntiteRepository demoEntiteRepository, HistoriqueService historiqueService) {
        this.demoEntiteRepository = demoEntiteRepository;
        this.historiqueService = historiqueService;
    }

    @GetMapping
    public PageReponse<DemoEntiteDto> lister(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String libelle,
            @RequestParam(required = false) String categorie,
            HttpServletRequest request) {

        Specification<DemoEntite> specification = Specification
                .<DemoEntite>where(SpecificationsBase.contientSiPresent("libelle", libelle))
                .and(SpecificationsBase.egalSiPresent("categorie", categorie));

        Pageable pageable = PaginationUtils.construire(page, size, PaginationUtils.extraireCriteresDeTri(request));

        Page<DemoEntiteDto> resultat = demoEntiteRepository.findAll(specification, pageable)
                .map(DemoEntiteMapper::versDto);

        return PageReponse.depuis(resultat);
    }

    /**
     * Démonstration de l'endpoint générique de consultation d'historique
     * (T-06, {@link HistoriqueService}) : chaque module métier expose sa
     * propre variante avec son type d'entité, {@code common} ne pouvant
     * importer aucune entité métier (CLAUDE.md, règle 1).
     */
    @GetMapping("/{id}/historique")
    public List<DemoEntiteHistoriqueDto> historique(@PathVariable UUID id) {
        return historiqueService.historique(DemoEntite.class, id).stream()
                .map(DemoEntiteMapper::versHistoriqueDto)
                .toList();
    }
}
