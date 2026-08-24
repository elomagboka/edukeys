package tg.novadigital.edukeys.common.multietablissement;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import tg.novadigital.edukeys.common.domain.EntiteEtablissement;
import tg.novadigital.edukeys.common.repository.BaseRepository;

/**
 * Couche de garde bruyante (sous-tâche 8, T-05) : toute méthode d'un
 * repository {@link BaseRepository} portant sur une entité
 * {@link EntiteEtablissement} échoue immédiatement si elle est invoquée sans
 * contexte d'établissement ouvert.
 *
 * <p><b>Pourquoi une couche en plus du filtre Hibernate.</b> Le filtre
 * (armé sur valeur de secours {@link ContexteEtablissement#ETABLISSEMENT_NIL}
 * quand aucun contexte n'est ouvert, voir {@code ArmeurFiltreEtablissement})
 * suffit techniquement à ne renvoyer aucune ligne. Mais un repository qui
 * répond silencieusement une liste vide, ou {@code Optional.empty()}, en
 * l'absence de contexte est indiscernable d'un repository qui répond
 * correctement « rien ne correspond » : l'appelant ne sait jamais s'il a
 * oublié d'ouvrir un contexte ou si la donnée n'existe vraiment pas. Un échec
 * bruyant ici se corrige en cinq minutes ; un échec silencieux se
 * transformerait en fuite de données constatée bien plus tard.</p>
 *
 * <p>Ne s'applique <strong>qu'aux entités {@code EntiteEtablissement}</strong> :
 * les quatre entités hors périmètre ({@code Etablissement}, {@code Utilisateur},
 * {@code AffectationEtablissement}, {@code JetonRafraichissement}) sont lues
 * pendant le login, sans contexte, et doivent le rester (ADR-0002, précision
 * d'implémentation T-05).</p>
 *
 * <p>{@code this(BaseRepository)} plutôt qu'un {@code execution(..)} sur
 * {@code BaseRepository+} : les repositories Spring Data sont des proxys JDK
 * dynamiques dont la liste d'interfaces déclarées ne porte, en général, que
 * l'interface la plus spécifique (ex. {@code DemoEntiteRepository}), pas
 * {@code BaseRepository} elle-même. {@code this()} teste une assignabilité
 * de type à l'exécution, robuste à ce détail d'implémentation.</p>
 */
@Aspect
@Component
public class GardeContexteEtablissement {

    @Before("this(tg.novadigital.edukeys.common.repository.BaseRepository)")
    public void verifierContexteOuvert(JoinPoint joinPoint) {
        Object repository = joinPoint.getThis();
        if (repository == null) {
            return;
        }

        InterfaceEtEntite resolu = resoudre(repository);
        if (resolu == null || !EntiteEtablissement.class.isAssignableFrom(resolu.typeEntite())) {
            return;
        }

        if (ContexteEtablissement.courant().isEmpty()) {
            throw new ContexteEtablissementAbsentException(
                    "Accès refusé : %s.%s() touche l'entité %s (EntiteEtablissement) sans contexte d'établissement ouvert."
                            .formatted(resolu.interfaceRepository().getSimpleName(),
                                    joinPoint.getSignature().getName(),
                                    resolu.typeEntite().getSimpleName()));
        }
    }

    /**
     * Résout l'interface repository la plus spécifique et le type d'entité
     * qu'elle porte, en remontant la hiérarchie d'interfaces jusqu'à
     * {@link BaseRepository} via {@link ResolvableType} (qui, contrairement à
     * {@code getGenericInterfaces()}, traverse les super-interfaces).
     */
    private InterfaceEtEntite resoudre(Object repository) {
        for (Class<?> candidate : repository.getClass().getInterfaces()) {
            ResolvableType resolvableType = ResolvableType.forClass(candidate).as(BaseRepository.class);
            if (resolvableType == ResolvableType.NONE) {
                continue;
            }
            Class<?> typeEntite = resolvableType.getGeneric(0).resolve();
            if (typeEntite != null) {
                return new InterfaceEtEntite(candidate, typeEntite);
            }
        }
        return null;
    }

    private record InterfaceEtEntite(Class<?> interfaceRepository, Class<?> typeEntite) {
    }
}
