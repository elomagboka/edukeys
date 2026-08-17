package tg.novadigital.edukeys.common.web.pagination;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Construit un {@link Pageable} à partir des paramètres de requête
 * {@code page}, {@code size} et {@code sort}, avec des bornes de sécurité :
 * taille de page plafonnée, page négative ramenée à zéro. Réutilisable par
 * tous les modules métier qui exposent des listes.
 */
public final class PaginationUtils {

    public static final int PAGE_DEFAUT = 0;
    public static final int TAILLE_PAGE_DEFAUT = 20;
    public static final int TAILLE_PAGE_MAX = 100;

    private PaginationUtils() {
    }

    /**
     * Récupère les valeurs brutes du paramètre {@code sort} directement sur
     * la requête HTTP, en contournant la conversion automatique de Spring MVC
     * qui scinderait à tort {@code ?sort=champ,desc} sur la virgule (une
     * liaison {@code List<String>} classique traite la virgule comme un
     * séparateur d'éléments, pas comme le séparateur champ/direction).
     */
    public static List<String> extraireCriteresDeTri(HttpServletRequest request) {
        String[] valeurs = request.getParameterValues("sort");
        return valeurs == null ? List.of() : Arrays.asList(valeurs);
    }

    /**
     * @param sort critères au format Spring habituel {@code champ,direction},
     *             répétable (ex. {@code ?sort=nom,asc&sort=id,desc}).
     */
    public static Pageable construire(Integer page, Integer size, List<String> sort) {
        int pageEffective = (page == null || page < 0) ? PAGE_DEFAUT : page;
        int tailleEffective = (size == null || size < 1)
                ? TAILLE_PAGE_DEFAUT
                : Math.min(size, TAILLE_PAGE_MAX);
        return PageRequest.of(pageEffective, tailleEffective, construireTri(sort));
    }

    private static Sort construireTri(List<String> sort) {
        if (sort == null || sort.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> ordres = new ArrayList<>();
        for (String critere : sort) {
            if (critere == null || critere.isBlank()) {
                continue;
            }
            String[] parties = critere.split(",");
            String champ = parties[0].trim();
            if (champ.isBlank()) {
                continue;
            }
            Sort.Direction direction = (parties.length > 1 && "desc".equalsIgnoreCase(parties[1].trim()))
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            ordres.add(new Sort.Order(direction, champ));
        }
        return ordres.isEmpty() ? Sort.unsorted() : Sort.by(ordres);
    }
}
