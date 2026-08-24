package tg.novadigital.edukeys.common.multietablissement;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Porteur du contexte multi-établissement, par thread. {@code ThreadLocal}
 * volontairement <strong>non</strong> {@code InheritableThreadLocal} : un
 * pool de threads (Tomcat, exécuteur planifié) ne doit jamais faire fuiter le
 * contexte d'une requête vers le thread qui traite la suivante — chaque
 * traitement doit ouvrir explicitement le sien.
 *
 * <p>Trois façons d'obtenir un établissement courant, distinguées par
 * {@link PerimetreEtablissement.Origine} : résolution automatique depuis le
 * JWT ({@code ContexteEtablissementFilter}, module {@code identite}),
 * ouverture explicite pour un traitement asynchrone/planifié, ou bascule
 * {@code SUPER_ADMIN} sur un établissement pour l'administrer.</p>
 */
public final class ContexteEtablissement {

    /** Filtre armé sans contexte ouvert : couche de secours, zéro ligne visible (sous-tâche 8 : couche bruyante à venir). */
    public static final UUID ETABLISSEMENT_NIL = new UUID(0L, 0L);

    private static final ThreadLocal<PerimetreEtablissement> COURANT = new ThreadLocal<>();

    /** Renseigné une seule fois au démarrage par {@link ArmeurFiltreEtablissement}, pour ré-armer le filtre à l'ouverture d'un contexte. */
    private static volatile EntityManagerFactory entityManagerFactory;

    private ContexteEtablissement() {
    }

    static void enregistrerEntityManagerFactory(EntityManagerFactory emf) {
        entityManagerFactory = emf;
    }

    public static Optional<PerimetreEtablissement> courant() {
        return Optional.ofNullable(COURANT.get());
    }

    public static UUID exigerEtablissementId() {
        PerimetreEtablissement perimetre = COURANT.get();
        if (perimetre == null) {
            throw new ContexteEtablissementAbsentException();
        }
        return perimetre.etablissementId();
    }

    /** Ouverture explicite (traitement asynchrone ou planifié). */
    public static PorteeEtablissement ouvrir(UUID etablissementId) {
        return ouvrir(etablissementId, PerimetreEtablissement.Origine.EXPLICITE);
    }

    public static PorteeEtablissement ouvrir(UUID etablissementId, PerimetreEtablissement.Origine origine) {
        PerimetreEtablissement precedent = COURANT.get();
        COURANT.set(new PerimetreEtablissement(etablissementId, origine));
        try {
            reArmerFiltreSurSessionLiee();
        } catch (RuntimeException echecArmement) {
            // Le ThreadLocal est déjà posé, mais l'appelant ne recevra jamais de
            // PorteeEtablissement : son try-with-resources n'existera pas, et le
            // finally du filtre servlet n'aura rien à fermer. Sans ce rattrapage,
            // le contexte fuiterait vers la requête suivante traitée par ce thread.
            // On ne re-passe pas par restaurer() : le ré-armement vient d'échouer,
            // le rejouer masquerait la cause première derrière une seconde exception.
            poser(precedent);
            throw echecArmement;
        }
        return new PorteeEtablissement(precedent);
    }

    /** Restaure un périmètre précédent (utilisé exclusivement par {@link PorteeEtablissement#close()}). */
    static void restaurer(PerimetreEtablissement precedent) {
        poser(precedent);
        reArmerFiltreSurSessionLiee();
    }

    /**
     * Écrit le périmètre dans le {@code ThreadLocal} sans toucher au filtre
     * Hibernate. {@code remove()} et non {@code set(null)} : l'entrée doit
     * disparaître de la map du thread, un thread de pool étant réutilisé.
     */
    private static void poser(PerimetreEtablissement perimetre) {
        if (perimetre == null) {
            COURANT.remove();
        } else {
            COURANT.set(perimetre);
        }
    }

    /**
     * Réservé au détecteur de fuite du profil {@code test}
     * ({@code DetecteurFuiteContexteFilter}) : signale un contexte encore ouvert
     * et nettoie, pour qu'un oubli ne fasse pas cascader les tests suivants.
     */
    static Optional<PerimetreEtablissement> purger() {
        PerimetreEtablissement restant = COURANT.get();
        COURANT.remove();
        return Optional.ofNullable(restant);
    }

    /**
     * Ré-arme le filtre sur l'{@code EntityManager} déjà lié au thread courant
     * (une transaction ouverte avant l'appel à {@code ouvrir}/{@code close}) :
     * sans cela, une session déjà créée continuerait de filtrer sur l'ancien
     * établissement jusqu'à sa fermeture.
     */
    private static void reArmerFiltreSurSessionLiee() {
        EntityManagerFactory emf = entityManagerFactory;
        if (emf == null) {
            return;
        }
        Object ressource = TransactionSynchronizationManager.getResource(emf);
        if (ressource instanceof EntityManagerHolder holder) {
            ArmeurFiltreEtablissement.armer(holder.getEntityManager());
        }
    }
}
