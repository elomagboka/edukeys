package tg.novadigital.edukeys.common.demo.web;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import tg.novadigital.edukeys.common.demo.domain.DemoEntite;
import tg.novadigital.edukeys.common.demo.mapper.DemoEntiteMapper;
import tg.novadigital.edukeys.common.demo.repository.DemoEntiteRepository;
import tg.novadigital.edukeys.common.specification.SpecificationsBase;
import tg.novadigital.edukeys.common.web.pagination.PageReponse;
import tg.novadigital.edukeys.common.web.pagination.PaginationUtils;

/**
 * Contrôleur de démonstration du socle de pagination/recherche (T-03).
 * N'est PAS une fonctionnalité métier : sert uniquement de support au test
 * d'intégration de la pagination, du tri et du filtrage dynamiques. Restreint
 * aux profils de développement et de test.
 */
@Profile({"local", "test"})
@RestController
@RequestMapping("/internal/demo/entites")
public class DemoEntiteController {

    private final DemoEntiteRepository demoEntiteRepository;

    public DemoEntiteController(DemoEntiteRepository demoEntiteRepository) {
        this.demoEntiteRepository = demoEntiteRepository;
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
}
