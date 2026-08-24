package tg.novadigital.edukeys.common.multietablissement;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.UUID;

import org.hibernate.Session;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Arme automatiquement le filtre Hibernate {@code filtreEtablissement} sur
 * chaque session créée, à partir de l'établissement courant
 * ({@link ContexteEtablissement}). Volontairement un {@link BeanPostProcessor}
 * qui proxyfie le bean {@code EntityManagerFactory}, pas un aspect sur
 * {@code @Transactional} : une session peut être créée en dehors de toute
 * méthode transactionnelle annotée (traitement manuel, batch), et le filtre
 * doit être armé dès sa création, avant toute requête.
 *
 * <p>Le bean {@code EntityManagerFactory} est remplacé une fois pour toutes
 * par ce proxy : c'est donc la même instance qui est injectée partout
 * (repositories, {@code JpaTransactionManager}...), ce qui permet à
 * {@link ContexteEtablissement} de retrouver, via
 * {@code TransactionSynchronizationManager.getResource(emf)}, l'
 * {@code EntityManager} déjà lié au thread courant pour le ré-armer.</p>
 */
@Component
public class ArmeurFiltreEtablissement implements BeanPostProcessor {

    private static final String NOM_FILTRE = "filtreEtablissement";
    private static final String PARAMETRE_FILTRE = "etablissementId";
    private static final String BEAN_ENTITY_MANAGER_FACTORY = "entityManagerFactory";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof EntityManagerFactory emf) || !BEAN_ENTITY_MANAGER_FACTORY.equals(beanName)) {
            return bean;
        }

        EntityManagerFactory proxy = (EntityManagerFactory) Proxy.newProxyInstance(
                emf.getClass().getClassLoader(),
                interfacesDe(emf),
                nouvelInvocationHandler(emf));

        ContexteEtablissement.enregistrerEntityManagerFactory(proxy);
        return proxy;
    }

    private static Class<?>[] interfacesDe(EntityManagerFactory emf) {
        // Toutes les interfaces implémentées par le bean réel, pas seulement
        // EntityManagerFactory : Spring s'appuie sur des marqueurs internes
        // (EntityManagerFactoryInfo notamment) pour reconnaître le bean comme
        // une véritable fabrique JPA Spring-géré. S'en tenir à
        // EntityManagerFactory seul fait perdre ces instanceof et casse
        // silencieusement la liaison transactionnelle (constaté en test
        // d'intégration T-05 : l'EntityManager injecté par @PersistenceContext
        // n'était plus le même que celui lié au thread par
        // JpaTransactionManager).
        return org.springframework.util.ClassUtils.getAllInterfacesForClass(emf.getClass());
    }

    private static InvocationHandler nouvelInvocationHandler(EntityManagerFactory cible) {
        return (proxyObj, method, args) -> {
            Object resultat;
            try {
                resultat = method.invoke(cible, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (resultat instanceof EntityManager entityManager && method.getName().startsWith("createEntityManager")) {
                armer(entityManager);
            }
            return resultat;
        };
    }

    /** Arme (ou ré-arme) le filtre sur l'{@code EntityManager} donné, à partir du contexte courant. Sans contexte ouvert : UUID nil, zéro ligne (couche de secours). */
    static void armer(EntityManager entityManager) {
        Session session = entityManager.unwrap(Session.class);
        UUID etablissementId = ContexteEtablissement.courant()
                .map(PerimetreEtablissement::etablissementId)
                .orElse(ContexteEtablissement.ETABLISSEMENT_NIL);
        session.enableFilter(NOM_FILTRE).setParameter(PARAMETRE_FILTRE, etablissementId);
    }
}
