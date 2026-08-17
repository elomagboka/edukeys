package tg.novadigital.edukeys.common.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Socle commun des repositories métier Edukeys.
 *
 * <p>Étend {@link Repository} (et non {@link org.springframework.data.jpa.repository.JpaRepository})
 * afin de ne redéclarer que les opérations de lecture/écriture autorisées.
 * Aucune méthode {@code delete*} n'est exposée : la suppression physique est
 * interdite, seule la désactivation logique via {@code EntiteDesactivable}
 * est permise (voir CLAUDE.md, règle 4).</p>
 *
 * <p>Les méthodes de recherche par {@link Specification} sont redéclarées ici
 * en lecture seule uniquement : {@code JpaSpecificationExecutor} n'est pas
 * étendu, car il expose {@code delete(Specification)}, une suppression en
 * masse traduite en DELETE SQL qui contourne le contexte de persistance
 * (audit Envers, futur filtre multi-établissement). Voir CLAUDE.md, règle 4.</p>
 *
 * <p>L'identifiant est fixé à {@link UUID} (UUID v7) pour toutes les entités
 * métier : voir CLAUDE.md, règle 3. Ce n'est pas une simple convention, le
 * compilateur l'impose.</p>
 *
 * @param <T> type de l'entité
 */
@NoRepositoryBean
public interface BaseRepository<T> extends Repository<T, UUID> {

    T save(T entity);

    Optional<T> findById(UUID id);

    boolean existsById(UUID id);

    List<T> findAll();

    Page<T> findAll(Pageable pageable);

    long count();

    Optional<T> findOne(Specification<T> spec);

    List<T> findAll(Specification<T> spec);

    Page<T> findAll(Specification<T> spec, Pageable pageable);

    List<T> findAll(Specification<T> spec, Sort sort);

    long count(Specification<T> spec);

    boolean exists(Specification<T> spec);
}
