package tg.novadigital.edukeys.eleve.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.eleve.domain.CompteurMatricule;
import tg.novadigital.edukeys.eleve.repository.CompteurMatriculeRepository;

/**
 * Crée la ligne du compteur de matricule à ZÉRO, et rien d'autre (US-08, même motif que la création
 * de {@code CompteurReferenceAdmission} en US-06). {@code SELECT ... FOR UPDATE} ne verrouille pas une ligne
 * inexistante : deux premières inscriptions de l'année tentent donc chacune l'insertion, et la perdante
 * viole {@code uk_compteurs_matricule_annee}. PostgreSQL annule la transaction fautive dès la violation ;
 * le rattrapage se fait donc hors de toute transaction, dans {@link InscriptionService}, qui appelle ce
 * composant AVANT d'ouvrir la transaction principale et tolère le conflit — la ligne y est alors
 * committée par la gagnante.
 *
 * <p>Ce composant n'incrémente JAMAIS : l'incrément se fait sous verrou dans la transaction principale
 * ({@link GenerateurMatricule}), pour que son rollback annule le numéro. Une ligne à zéro committée
 * même si l'inscription échoue ne consomme aucun numéro.</p>
 */
@Component
public class CreateurLigneCompteurMatricule {

    /** Unicité de la ligne de compteur (une par établissement et par année de début d'année scolaire). */
    public static final String CONTRAINTE_COMPTEUR = "uk_compteurs_matricule_annee";

    private final CompteurMatriculeRepository compteurMatriculeRepository;
    private final EntityManager entityManager;

    public CreateurLigneCompteurMatricule(CompteurMatriculeRepository compteurMatriculeRepository, EntityManager entityManager) {
        this.compteurMatriculeRepository = compteurMatriculeRepository;
        this.entityManager = entityManager;
    }

    /**
     * Idempotent. Doit être appelé hors transaction (ou dans une transaction jetable) : une violation
     * {@value #CONTRAINTE_COMPTEUR} remonte telle quelle, après flush explicite, à l'appelant qui la tolère.
     */
    @Transactional
    public void assurerExistence(UUID etablissementId, int anneeDebut) {
        if (compteurMatriculeRepository.existsByEtablissementIdAndAnnee(etablissementId, anneeDebut)) {
            return;
        }
        compteurMatriculeRepository.save(new CompteurMatricule(etablissementId, anneeDebut));
        // Flush immédiat : la violation d'une course doit remonter ICI (et non au commit, hors de portée).
        entityManager.flush();
    }
}
