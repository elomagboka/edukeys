package tg.novadigital.edukeys.common.audit;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.envers.boot.internal.EnversService;
import org.hibernate.envers.internal.entities.EntitiesConfigurations;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import tg.novadigital.edukeys.common.domain.BaseEntity;

/**
 * Fait échouer le démarrage plutôt qu'une historisation silencieusement
 * absente (même philosophie que {@code VerificateurFiltreEtablissement},
 * T-05) : contrairement au filtre Hibernate multi-établissement,
 * {@code @Audited} posé sur {@code BaseEntity} (ou sur
 * {@code EntiteEtablissement}, {@code @MappedSuperclass} intermédiaire) ne se
 * propage <b>pas</b> aux sous-classes avec cette version d'Hibernate Envers —
 * constaté sur les cinq entités métier existantes, toutes restées non
 * auditées (Envers levait {@code NotAuditedException}) tant que
 * {@code @Audited} n'était pas répété directement sur chaque classe concrète.
 * Plutôt que documenter ce piège et compter sur la vigilance du prochain
 * module métier, ce vérificateur refuse de démarrer si une entité l'a oublié.
 */
@Component
public class VerificateurAuditEnvers implements ApplicationRunner {

    private final EntityManagerFactory entityManagerFactory;

    public VerificateurAuditEnvers(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EnversService enversService = sessionFactory.getServiceRegistry().getService(EnversService.class);
        EntitiesConfigurations entitesAuditees = enversService.getEntitiesConfigurations();

        List<String> entitesNonAuditees = new ArrayList<>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            Class<?> classeEntite = entityType.getJavaType();
            if (classeEntite == null || !BaseEntity.class.isAssignableFrom(classeEntite)) {
                continue;
            }
            if (!entitesAuditees.isVersioned(classeEntite.getName())) {
                entitesNonAuditees.add(classeEntite.getName());
            }
        }

        if (!entitesNonAuditees.isEmpty()) {
            throw new IllegalStateException(
                    "@Audited (Hibernate Envers) n'est pas actif sur les entites suivantes, alors qu'elles "
                            + "etendent BaseEntity : " + entitesNonAuditees + ". @Audited pose sur BaseEntity ou "
                            + "EntiteEtablissement (@MappedSuperclass) ne se propage pas aux sous-classes : "
                            + "ajoutez @Audited explicitement sur chaque classe d'entite concrete (T-06, voir "
                            + "Etablissement ou DemoEntite).");
        }
    }
}
