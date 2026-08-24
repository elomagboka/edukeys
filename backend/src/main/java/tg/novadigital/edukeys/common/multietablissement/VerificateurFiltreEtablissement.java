package tg.novadigital.edukeys.common.multietablissement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.metamodel.spi.MappingMetamodelImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Fait échouer le démarrage plutôt qu'une isolation silencieusement absente
 * (ADR-0002, §"risque principal et sa parade") : Hibernate n'hérite pas
 * toujours {@code @Filter} depuis une {@code @MappedSuperclass} selon les
 * versions — mieux vaut refuser de démarrer que servir une entité non
 * filtrée. Vérifie aussi que le cache de second niveau et le cache de requête
 * sont désactivés : un {@code @Filter} ne fait pas partie de la clé de cache,
 * donc un résultat mis en cache pour l'établissement A pourrait être resservi
 * à l'établissement B.
 */
@Component
public class VerificateurFiltreEtablissement implements ApplicationRunner {

    private static final String NOM_FILTRE = "filtreEtablissement";

    private final EntityManagerFactory entityManagerFactory;

    public VerificateurFiltreEtablissement(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        verifierAbsenceDeCachePartage();
        verifierFiltreArmeSurToutesLesEntitesEtablissement();
    }

    private void verifierAbsenceDeCachePartage() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        var options = sessionFactory.getSessionFactoryOptions();
        List<String> violations = new ArrayList<>();
        if (options.isSecondLevelCacheEnabled()) {
            violations.add("hibernate.cache.use_second_level_cache est actif");
        }
        if (options.isQueryCacheEnabled()) {
            violations.add("hibernate.cache.use_query_cache est actif");
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Configuration incompatible avec le filtre multi-établissement (@Filter n'entre pas dans "
                            + "la clé de cache) : " + String.join(", ", violations) + ". Voir CLAUDE.md et T-05.");
        }
    }

    private void verifierFiltreArmeSurToutesLesEntitesEtablissement() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        MappingMetamodelImplementor mappingMetamodel = sessionFactory.getMappingMetamodel();

        List<String> entitesNonFiltrees = new ArrayList<>();

        try (Session session = sessionFactory.openSession()) {
            session.enableFilter(NOM_FILTRE).setParameter("etablissementId", new UUID(0L, 0L));
            LoadQueryInfluencers influenceurs = ((SessionImplementor) session).getLoadQueryInfluencers();

            for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
                Class<?> classeEntite = entityType.getJavaType();
                if (classeEntite == null || !EntiteEtablissement.class.isAssignableFrom(classeEntite)) {
                    continue;
                }

                EntityPersister persister = mappingMetamodel.getEntityDescriptor(classeEntite);
                boolean filtreActif = persister instanceof AbstractEntityPersister abstractPersister
                        && abstractPersister.isAffectedByEnabledFilters(influenceurs, false);

                if (!filtreActif) {
                    entitesNonFiltrees.add(classeEntite.getName());
                }
            }
        }

        if (!entitesNonFiltrees.isEmpty()) {
            throw new IllegalStateException(
                    "Le filtre Hibernate '" + NOM_FILTRE + "' n'est pas actif sur les entités suivantes, "
                            + "alors qu'elles étendent EntiteEtablissement : " + entitesNonFiltrees
                            + ". Ajoutez explicitement @Filter(name = \"" + NOM_FILTRE + "\") sur chacune de ces "
                            + "entités : l'héritage depuis @MappedSuperclass n'est pas garanti par toutes les "
                            + "versions d'Hibernate (voir CLAUDE.md et T-05).");
        }
    }
}
