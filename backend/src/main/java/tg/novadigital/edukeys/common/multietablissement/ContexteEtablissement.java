package tg.novadigital.edukeys.common.multietablissement;

import java.util.Optional;
import java.util.UUID;

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

    /**
     * <strong>Aucun {@code EntityManagerFactory} n'est mémorisé ici, et c'est
     * volontaire.</strong> Ce champ a existé — un unique emplacement
     * {@code static volatile} renseigné par {@link ArmeurFiltreEtablissement}
     * au démarrage — et il portait un défaut que son propre commentaire
     * décrivait sans le corriger : « ce champ est static, donc partagé par
     * tout le run JVM, y compris entre classes de test qui réutilisent un
     * contexte Spring mis en cache ». Le contournement d'alors se bornait à
     * demander aux tests qui l'écrasaient de restaurer la valeur d'origine.
     *
     * <p>Il ne couvrait pas le cas réel : <strong>chaque contexte Spring
     * supplémentaire écrase l'emplacement</strong>, sans que personne ne
     * l'écrase « à la main ». Dès qu'une classe de test surcharge une
     * propriété ({@code @SpringBootTest(properties = ...)}), un second
     * contexte démarre, y inscrit sa propre fabrique, et toute classe
     * réutilisant le contexte précédent perd sa capacité à ré-armer le filtre
     * — {@code getResource(emf)} ne trouve plus rien et sortait
     * <em>en silence</em>. Résultat constaté : le filtre Hibernate
     * n'était plus armé du tout pendant {@code IsolationEtablissementTest},
     * qui devenait vert sans rien prouver, et {@code findById} traversait la
     * frontière entre établissements. Invisible pendant trois mois : l'ordre
     * d'exécution Surefire par défaut ({@code filesystem}) place la classe
     * fautive après sous NTFS, avant sous ext4.
     *
     * <p>La correction supprime l'état global au lieu de le discipliner :
     * {@link #reArmerFiltreSurSessionLiee()} parcourt désormais les
     * ressources réellement liées au thread courant. Il n'y a plus rien à
     * écraser, donc plus de collision possible entre contextes. En
     * production, où un seul contexte existe, le comportement est
     * inchangé.</p>
     */
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
     * Ré-arme le filtre sur chaque {@code EntityManager} déjà lié au thread
     * courant (une transaction ouverte avant l'appel à {@code ouvrir} /
     * {@code close}) : sans cela, une session déjà créée continuerait de
     * filtrer sur l'ancien établissement jusqu'à sa fermeture.
     *
     * <p>On parcourt toutes les ressources liées plutôt que d'interroger une
     * fabrique mémorisée : voir le commentaire en tête de classe. En
     * production la boucle ne rencontre qu'un seul {@code EntityManagerHolder} ;
     * en test elle rend l'armement indépendant du nombre de contextes Spring
     * en vie.</p>
     */
    private static void reArmerFiltreSurSessionLiee() {
        for (Object ressource : TransactionSynchronizationManager.getResourceMap().values()) {
            if (ressource instanceof EntityManagerHolder holder) {
                ArmeurFiltreEtablissement.armer(holder.getEntityManager());
            }
        }
    }
}
