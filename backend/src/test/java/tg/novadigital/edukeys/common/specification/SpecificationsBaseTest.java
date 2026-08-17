package tg.novadigital.edukeys.common.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class SpecificationsBaseTest {

    @SuppressWarnings("unchecked")
    private final Root<Object> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);

    @Test
    void egalSiPresent_neConstruitAucunPredicat_quandValeurNulle() {
        Specification<Object> specification = SpecificationsBase.egalSiPresent("categorie", null);

        Predicate predicat = specification.toPredicate(root, query, criteriaBuilder);

        assertThat(predicat).isNull();
        verifyNoInteractions(criteriaBuilder);
    }

    @SuppressWarnings("unchecked")
    @Test
    void egalSiPresent_construitUneEgaliteStricte_quandValeurFournie() {
        Path<Object> chemin = mock(Path.class);
        Predicate predicatAttendu = mock(Predicate.class);
        when(root.get("categorie")).thenReturn(chemin);
        when(criteriaBuilder.equal(chemin, "sciences")).thenReturn(predicatAttendu);

        Specification<Object> specification = SpecificationsBase.egalSiPresent("categorie", "sciences");
        Predicate predicat = specification.toPredicate(root, query, criteriaBuilder);

        assertThat(predicat).isSameAs(predicatAttendu);
    }

    @Test
    void contientSiPresent_neConstruitAucunPredicat_quandValeurVide() {
        Specification<Object> specification = SpecificationsBase.contientSiPresent("libelle", "  ");

        Predicate predicat = specification.toPredicate(root, query, criteriaBuilder);

        assertThat(predicat).isNull();
        verifyNoInteractions(criteriaBuilder);
    }

    @SuppressWarnings("unchecked")
    @Test
    void contientSiPresent_construitUneRechercheInsensibleALaCasse_quandValeurFournie() {
        Path<String> chemin = mock(Path.class);
        jakarta.persistence.criteria.Expression<String> expressionMinuscule = mock(jakarta.persistence.criteria.Expression.class);
        Predicate predicatAttendu = mock(Predicate.class);
        when(root.<String>get("libelle")).thenReturn(chemin);
        when(criteriaBuilder.lower(chemin)).thenReturn(expressionMinuscule);
        when(criteriaBuilder.like(expressionMinuscule, "%alpha%")).thenReturn(predicatAttendu);

        Specification<Object> specification = SpecificationsBase.contientSiPresent("libelle", "Alpha");
        Predicate predicat = specification.toPredicate(root, query, criteriaBuilder);

        assertThat(predicat).isSameAs(predicatAttendu);
        verify(criteriaBuilder).like(expressionMinuscule, "%alpha%");
    }

    @SuppressWarnings("unchecked")
    @Test
    void actifSeulement_construitUnFiltreSurLeChampActif() {
        Path<Object> chemin = mock(Path.class);
        Predicate predicatAttendu = mock(Predicate.class);
        when(root.get("actif")).thenReturn(chemin);
        when(criteriaBuilder.isTrue(any())).thenReturn(predicatAttendu);

        Specification<Object> specification = SpecificationsBase.actifSeulement();
        Predicate predicat = specification.toPredicate(root, query, criteriaBuilder);

        assertThat(predicat).isSameAs(predicatAttendu);
    }
}
