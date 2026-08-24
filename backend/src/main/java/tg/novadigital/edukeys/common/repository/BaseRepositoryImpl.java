package tg.novadigital.edukeys.common.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * Implémentation de base des repositories métier Edukeys, branchée via
 * {@code JpaRepositoriesConfig} (sous-tâche 8, T-05).
 *
 * <p><b>Angle mort A1 comblé ici</b> (ADR-0002, §2) :
 * {@link EntityManager#find(Class, Object)} — utilisé par
 * {@code SimpleJpaRepository.findById} — passe par le cache de premier
 * niveau et par un chargement direct par identifiant qui
 * <strong>ignore les {@code @Filter} Hibernate</strong>. C'est le trou le
 * plus exploitable du dispositif multi-établissement : un accès direct à
 * l'identifiant d'une entité d'un autre établissement aboutirait. On
 * réécrit donc {@code findById} en API Criteria — une requête, donc soumise
 * au filtre — plutôt que de garder l'implémentation héritée.</p>
 *
 * <p>Aucune autre méthode de {@link BaseRepository} n'emprunte
 * {@code EntityManager.find}/{@code getReference} : {@code findAll},
 * {@code count}, {@code exists}, {@code findOne(Specification)} et leurs
 * variantes paginées/triées passent déjà par l'API Criteria dans
 * {@code SimpleJpaRepository}, donc par le filtre. {@code BaseRepository}
 * n'expose ni {@code getReferenceById} ni {@code getOne} : ces méthodes,
 * elles aussi fondées sur {@code EntityManager.getReference}, ne sont
 * simplement jamais redéclarées.</p>
 *
 * <p>Fixé sur {@link UUID} plutôt que générique sur l'identifiant : CLAUDE.md
 * (règle 3) impose l'UUID v7 à toutes les entités métier, et
 * {@link BaseRepository} fixe déjà {@code ID = UUID} en étendant
 * {@code Repository<T, UUID>}. Un second paramètre de type ne ferait
 * qu'introduire un risque d'erreur d'effacement de type au branchement par
 * {@code repositoryBaseClass}.</p>
 *
 * @param <T> type de l'entité
 */
public class BaseRepositoryImpl<T> extends SimpleJpaRepository<T, UUID> {

    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ?> entityInformation;

    public BaseRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findById(UUID id) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = criteriaBuilder.createQuery(entityInformation.getJavaType());
        Root<T> root = query.from(entityInformation.getJavaType());
        query.where(criteriaBuilder.equal(root.get(entityInformation.getIdAttribute()), id));
        try {
            return Optional.of(entityManager.createQuery(query).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
