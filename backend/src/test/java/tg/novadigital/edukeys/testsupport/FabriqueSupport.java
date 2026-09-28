package tg.novadigital.edukeys.testsupport;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;

/**
 * Point d'accès partagé à l'{@link EntityManager} du test en cours, pour les
 * fabriques dont l'entité porte une relation {@code @ManyToOne} obligatoire
 * vers une autre entité filtrée (US-02 : {@code Niveau -> Cycle},
 * {@code Classe -> Niveau/AnneeScolaire}) — {@link FabriqueEntiteEtablissement#creer}
 * ne reçoit qu'un {@code etablissementId}, pas d'{@code EntityManager} ; les
 * fabriques antérieures (Site, Logo, AnneeScolaire) n'en avaient pas besoin,
 * aucune relation obligatoire vers une autre entité filtrée.
 *
 * <p>Renseigné par {@code IsolationEtablissementTest} avant chaque test, dans
 * le même contexte transactionnel que les persistances ultérieures : la
 * relation créée ici (ex. un {@code Cycle}) est donc visible par la même
 * session Hibernate que celle qui persistera ensuite le {@code Niveau} qui la
 * référence.</p>
 */
public final class FabriqueSupport {

    private static EntityManager entityManager;

    private static final AtomicInteger PROCHAINE_ANNEE = new AtomicInteger(2000);

    private FabriqueSupport() {
    }

    public static void definir(EntityManager em) {
        entityManager = em;
    }

    public static EntityManager entityManager() {
        return entityManager;
    }

    /**
     * Année de début unique dans la JVM, pour toute fabrique qui persiste une
     * année scolaire (contrainte d'exclusion {@code ex_annees_scolaires_chevauchement}
     * par établissement). Un tirage aléatoire sur 1000 ans entrait en collision
     * dès que cinq fabriques en créaient dans le même établissement (US-07).
     */
    public static int anneeDebutUnique() {
        return PROCHAINE_ANNEE.getAndIncrement();
    }
}
