package tg.novadigital.edukeys.admission.service;

import java.time.Year;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.admission.domain.CompteurReferenceAdmission;
import tg.novadigital.edukeys.admission.repository.CompteurReferenceAdmissionRepository;

/**
 * Génère les références de dossier {@code PRE-<annee>-<sequence>} (US-06),
 * format {@code PRE-2026-000123}. La séquence est portée par
 * {@link CompteurReferenceAdmission}, incrémentée sous verrou pessimiste
 * (JPQL) — jamais de SQL natif sur cette entité métier (CLAUDE.md, règle 2).
 */
@Service
public class GenerateurReferenceAdmission {

    private final CompteurReferenceAdmissionRepository compteurReferenceAdmissionRepository;
    private final EntityManager entityManager;

    public GenerateurReferenceAdmission(CompteurReferenceAdmissionRepository compteurReferenceAdmissionRepository,
            EntityManager entityManager) {
        this.compteurReferenceAdmissionRepository = compteurReferenceAdmissionRepository;
        this.entityManager = entityManager;
    }

    /**
     * Exécuté dans la transaction appelante (pas de {@code REQUIRES_NEW}) :
     * la séquence attribuée ne doit pas être consommée si le reste de la
     * soumission échoue et provoque un rollback (règle 2 de la spec US-06,
     * soumission atomique).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String genererReference(UUID etablissementId) {
        int annee = Year.now().getValue();
        CompteurReferenceAdmission compteur = compteurReferenceAdmissionRepository
                .trouverPourVerrouiller(etablissementId, annee)
                // Ligne absente (première soumission de l'établissement, ou passage
                // d'année) : SELECT ... FOR UPDATE ne verrouille rien d'inexistant, deux
                // soumissions simultanées tentent donc chacune l'insertion. La perdante
                // viole uk_compteurs_reference_admission_annee ; ce n'est pas rattrapable
                // ici (PostgreSQL annule la transaction dès la violation), c'est
                // DemandeAdmissionService qui rejoue l'insertion dans une transaction
                // neuve — la ligne y est alors présente, committée par la gagnante.
                .orElseGet(() -> creerLaLigneDeCompteur(etablissementId, annee));
        long sequence = compteur.incrementerEtObtenir();
        compteurReferenceAdmissionRepository.save(compteur);
        return "PRE-%d-%06d".formatted(annee, sequence);
    }


    private CompteurReferenceAdmission creerLaLigneDeCompteur(UUID etablissementId, int annee) {
        CompteurReferenceAdmission compteur =
                compteurReferenceAdmissionRepository.save(new CompteurReferenceAdmission(etablissementId, annee));
        // Flush immédiat : sans lui, l'insertion part au commit, et la violation
        // d'unicité d'une course sur la ligne absente remonterait trop tard pour
        // que DemandeAdmissionService la reconnaisse et rejoue l'insertion.
        entityManager.flush();
        return compteur;
    }
}
