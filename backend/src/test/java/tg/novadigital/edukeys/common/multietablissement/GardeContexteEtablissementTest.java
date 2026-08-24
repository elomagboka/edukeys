package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.common.demo.domain.DemoEntite;
import tg.novadigital.edukeys.common.demo.repository.DemoEntiteRepository;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;

/**
 * Unitaire de {@link GardeContexteEtablissement} (sous-tâche 8, T-05), sans
 * démarrer de contexte Spring : construit des proxys JDK minimaux imitant
 * ceux produits par Spring Data pour {@code DemoEntiteRepository} (entité
 * {@code EntiteEtablissement}) et {@code EtablissementRepository} (hors
 * périmètre du filtre).
 */
class GardeContexteEtablissementTest {

    private final GardeContexteEtablissement garde = new GardeContexteEtablissement();

    @AfterEach
    void nettoyer() {
        ContexteEtablissement.courant().ifPresent(p -> ContexteEtablissement.restaurer(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyRepository(Class<T> repositoryInterface) {
        return (T) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface},
                (InvocationHandler) (proxyObj, method, args) -> null);
    }

    private static JoinPoint joinPointPour(Object repository, String nomMethode) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getThis()).thenReturn(repository);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(nomMethode);
        return joinPoint;
    }

    @Test
    void refuse_l_appel_sur_une_entite_etablissement_sans_contexte_ouvert() {
        DemoEntiteRepository repository = proxyRepository(DemoEntiteRepository.class);
        JoinPoint joinPoint = joinPointPour(repository, "findAll");

        assertThatThrownBy(() -> garde.verifierContexteOuvert(joinPoint))
                .isInstanceOf(ContexteEtablissementAbsentException.class)
                .hasMessageContaining("DemoEntiteRepository")
                .hasMessageContaining("findAll")
                .hasMessageContaining(DemoEntite.class.getSimpleName());
    }

    @Test
    void laisse_passer_l_appel_sur_une_entite_etablissement_avec_contexte_ouvert() {
        DemoEntiteRepository repository = proxyRepository(DemoEntiteRepository.class);
        JoinPoint joinPoint = joinPointPour(repository, "findAll");

        try (var portee = ContexteEtablissement.ouvrir(UUID.randomUUID())) {
            garde.verifierContexteOuvert(joinPoint); // ne lève pas
        }
    }

    @Test
    void laisse_passer_les_entites_hors_perimetre_meme_sans_contexte_ouvert() {
        EtablissementRepository repository = proxyRepository(EtablissementRepository.class);
        JoinPoint joinPoint = joinPointPour(repository, "findAll");

        assertThat(ContexteEtablissement.courant()).isEmpty();
        garde.verifierContexteOuvert(joinPoint); // ne lève pas : Etablissement n'étend pas EntiteEtablissement
    }
}
