package tg.novadigital.edukeys.admission.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.admission.DossierAdmissionInscription;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.admission.repository.DemandeAdmissionRepository;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;

/** Implémentation du port {@link DossierAdmissionInscription} (US-08). */
@Service
public class DossierAdmissionInscriptionService implements DossierAdmissionInscription {

    private final DemandeAdmissionRepository demandeAdmissionRepository;

    public DossierAdmissionInscriptionService(DemandeAdmissionRepository demandeAdmissionRepository) {
        this.demandeAdmissionRepository = demandeAdmissionRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DossierPourInscription verrouillerPourInscription(UUID demandeId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        DemandeAdmission d = demandeAdmissionRepository.trouverPourVerrouiller(demandeId, etablissementId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_INTROUVABLE, "Demande d'admission introuvable."));
        return new DossierPourInscription(
                d.getId(), d.getVersion(), d.getStatut().name(), d.getStatut() == StatutAdmission.ACCEPTEE, d.getEleveId(),
                d.getAnneeScolaireId(), d.getNiveauId(), d.getClasseId(),
                d.getNom(), d.getPrenoms(), d.getDateNaissance(), d.getLieuNaissance(), d.getSexe(), d.getNationalite(),
                d.getEtablissementOrigine());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void marquerInscrite(UUID demandeId, UUID eleveId, Instant dateInscription) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        // Dossier déjà verrouillé et chargé par verrouillerPourInscription dans la même transaction : findById
        // (em.find) le sert depuis la session, sans SELECT ... FOR UPDATE supplémentaire. Le contrôle
        // d'établissement est refait ici : em.find ne passe pas par le prédicat de la requête de verrouillage.
        DemandeAdmission d = demandeAdmissionRepository.findById(demandeId)
                .filter(demande -> demande.isActif() && etablissementId.equals(demande.getEtablissementId()))
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_INTROUVABLE, "Demande d'admission introuvable."));
        d.marquerInscrite(eleveId, dateInscription);
        demandeAdmissionRepository.save(d);
    }
}
