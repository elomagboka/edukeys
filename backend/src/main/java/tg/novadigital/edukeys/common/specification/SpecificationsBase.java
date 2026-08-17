package tg.novadigital.edukeys.common.specification;

import org.springframework.data.jpa.domain.Specification;

/**
 * Briques réutilisables pour bâtir des filtres dynamiques ({@link Specification})
 * sur les listes de recherche multicritères. Chaque module métier compose ses
 * propres spécifications à partir de celles-ci — aucune requête native, le
 * filtre multi-établissement (T-05) reste applicable.
 */
public final class SpecificationsBase {

    private SpecificationsBase() {
    }

    /** Égalité stricte, ignorée si {@code valeur} est {@code null}. */
    public static <T> Specification<T> egalSiPresent(String champ, Object valeur) {
        return (root, query, cb) -> valeur == null ? null : cb.equal(root.get(champ), valeur);
    }

    /** Comparaison insensible à la casse par sous-chaîne, ignorée si vide. */
    public static <T> Specification<T> contientSiPresent(String champ, String valeur) {
        return (root, query, cb) -> (valeur == null || valeur.isBlank())
                ? null
                : cb.like(cb.lower(root.get(champ)), "%" + valeur.toLowerCase() + "%");
    }

    /** Filtre les entités actives (désactivation logique, cf. {@code EntiteDesactivable}). */
    public static <T> Specification<T> actifSeulement() {
        return (root, query, cb) -> cb.isTrue(root.get("actif"));
    }
}
