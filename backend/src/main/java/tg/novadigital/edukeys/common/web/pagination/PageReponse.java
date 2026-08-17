package tg.novadigital.edukeys.common.web.pagination;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Enveloppe de réponse paginée maison. Ne jamais renvoyer un {@link Page}
 * Spring directement en sortie de controller : sa sérialisation JSON n'est
 * pas stable entre versions de Spring Data.
 */
public record PageReponse<T>(
        List<T> contenu,
        int page,
        int taille,
        long totalElements,
        int totalPages) {

    public static <T> PageReponse<T> depuis(Page<T> page) {
        return new PageReponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
