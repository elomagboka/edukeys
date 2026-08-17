package tg.novadigital.edukeys.common.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginationUtilsTest {

    @Test
    void appliqueLaPageEtLaTailleParDefaut_quandAucunParametreFourni() {
        Pageable pageable = PaginationUtils.construire(null, null, null);

        assertThat(pageable.getPageNumber()).isEqualTo(PaginationUtils.PAGE_DEFAUT);
        assertThat(pageable.getPageSize()).isEqualTo(PaginationUtils.TAILLE_PAGE_DEFAUT);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void ramenePageAZero_quandPageNegative() {
        Pageable pageable = PaginationUtils.construire(-5, 20, null);

        assertThat(pageable.getPageNumber()).isZero();
    }

    @Test
    void appliqueLaTailleDemandee_quandTailleValide() {
        Pageable pageable = PaginationUtils.construire(0, 50, null);

        assertThat(pageable.getPageSize()).isEqualTo(50);
    }

    @Test
    void plafonneLaTailleA100_quandTailleDemandeeDepasseLeMaximum() {
        Pageable pageable = PaginationUtils.construire(0, 500, null);

        assertThat(pageable.getPageSize()).isEqualTo(PaginationUtils.TAILLE_PAGE_MAX);
    }

    @Test
    void appliqueLaTailleParDefaut_quandTailleInferieureA1() {
        Pageable pageable = PaginationUtils.construire(0, 0, null);

        assertThat(pageable.getPageSize()).isEqualTo(PaginationUtils.TAILLE_PAGE_DEFAUT);
    }

    @Test
    void construitUnTriAscendantParDefaut_quandDirectionNonPrecisee() {
        Pageable pageable = PaginationUtils.construire(0, 20, List.of("nom"));

        assertThat(pageable.getSort()).containsExactly(Sort.Order.asc("nom"));
    }

    @Test
    void construitUnTriDescendant_quandDirectionPrecisee() {
        Pageable pageable = PaginationUtils.construire(0, 20, List.of("nom,desc"));

        assertThat(pageable.getSort()).containsExactly(Sort.Order.desc("nom"));
    }

    @Test
    void construitPlusieursCriteresDeTri_quandPlusieursCritèresFournis() {
        Pageable pageable = PaginationUtils.construire(0, 20, List.of("nom,asc", "id,desc"));

        assertThat(pageable.getSort()).containsExactly(
                Sort.Order.asc("nom"),
                Sort.Order.desc("id"));
    }

    @Test
    void ignoreLesCriteresDeTriVidesOuBlancs() {
        Pageable pageable = PaginationUtils.construire(0, 20, List.of("", "   ", "nom,asc"));

        assertThat(pageable.getSort()).containsExactly(Sort.Order.asc("nom"));
    }
}
