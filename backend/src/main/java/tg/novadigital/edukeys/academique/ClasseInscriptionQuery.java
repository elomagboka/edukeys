package tg.novadigital.edukeys.academique;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port exposé par {@code academique} au module {@code eleve} (US-08) : lecture d'une
 * classe <strong>sous verrou</strong> pour y inscrire un élève. Aucune entité n'en sort
 * (CLAUDE.md, règle 1). Opère sur l'établissement du contexte courant.
 */
public interface ClasseInscriptionQuery {

    /**
     * Verrouille la SEULE ligne {@code classes} ({@code PESSIMISTIC_WRITE}, sans jointure : un
     * {@code FOR UPDATE} avec jointure verrouillerait aussi niveaux, filières et années, et
     * sérialiserait toutes les classes d'un même niveau), puis lit les libellés sans verrou.
     * Le verrou sérialise le comptage des inscrits et l'ajout d'un inscrit : tout futur chemin
     * qui ajoute un inscrit à une classe (US-11, transfert) DOIT prendre le même verrou, sinon
     * l'effectif maximal peut être dépassé.
     *
     * <p>Exige la transaction de l'inscription ({@code MANDATORY}). Ordre des verrous à respecter
     * partout : dossier, puis classe, puis compteur de matricule.</p>
     *
     * @throws tg.novadigital.edukeys.common.exception.RessourceIntrouvableException si la classe est
     *         absente ou d'un autre établissement
     */
    ClassePourInscription verrouillerPourInscription(UUID classeId);

    /**
     * Début de l'année scolaire de la classe, en lecture simple (sans verrou, sans exigence de transaction) :
     * sert à l'orchestrateur d'inscription pour garantir l'existence de la ligne du compteur de matricule
     * AVANT d'ouvrir la transaction principale. Vide si la classe est absente ou d'un autre établissement
     * (la transaction principale produira alors le 404, dans l'ordre des vérifications).
     */
    Optional<LocalDate> debutAnneeDeLaClasse(UUID classeId);

    /**
     * @param actif {@code false} si la classe est désactivée
     * @param anneeDateDebut début de l'année scolaire de la classe (l'année de début entre dans le matricule)
     * @param anneeModifiable l'année n'est pas CLOTUREE
     * @param siteId site de la classe, recopié dans l'inscription
     * @param effectifMax {@code null} : sans limite
     */
    record ClassePourInscription(
            UUID id,
            String libelle,
            boolean actif,
            UUID niveauId,
            String niveauLibelle,
            UUID filiereId,
            String filiereLibelle,
            UUID anneeScolaireId,
            String anneeLibelle,
            LocalDate anneeDateDebut,
            boolean anneeModifiable,
            UUID siteId,
            Integer effectifMax) {
    }
}
