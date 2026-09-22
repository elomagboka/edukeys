package tg.novadigital.edukeys.admission.service;

import java.time.Year;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    public GenerateurReferenceAdmission(CompteurReferenceAdmissionRepository compteurReferenceAdmissionRepository) {
        this.compteurReferenceAdmissionRepository = compteurReferenceAdmissionRepository;
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
                .orElseGet(() -> compteurReferenceAdmissionRepository.save(new CompteurReferenceAdmission(etablissementId, annee)));
        long sequence = compteur.incrementerEtObtenir();
        compteurReferenceAdmissionRepository.save(compteur);
        return "PRE-%d-%06d".formatted(annee, sequence);
    }
}
