package tg.novadigital.edukeys.academique.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.academique.ClasseInscriptionQuery;
import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;

/** Implémentation du port {@link ClasseInscriptionQuery} (US-08). */
@Service
public class ClasseInscriptionService implements ClasseInscriptionQuery {

    private final ClasseRepository classeRepository;

    public ClasseInscriptionService(ClasseRepository classeRepository) {
        this.classeRepository = classeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> debutAnneeDeLaClasse(UUID classeId) {
        return classeRepository.lireLibelles(classeId).map(ClasseRepository.LibellesClasse::anneeDateDebut);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ClassePourInscription verrouillerPourInscription(UUID classeId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        // 1. Verrou sur la seule ligne classes ; 2. libellés sans verrou (une requête).
        Classe classe = classeRepository.trouverPourVerrouiller(classeId, etablissementId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.CLASSE_INTROUVABLE, "Classe introuvable."));
        ClasseRepository.LibellesClasse l = classeRepository.lireLibelles(classeId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.CLASSE_INTROUVABLE, "Classe introuvable."));
        return new ClassePourInscription(
                classe.getId(), classe.getLibelle(), classe.isActif(),
                l.niveauId(), l.niveauLibelle(), l.filiereId(), l.filiereLibelle(),
                l.anneeScolaireId(), l.anneeLibelle(), l.anneeDateDebut(), l.anneeStatut() != StatutAnneeScolaire.CLOTUREE,
                classe.getSiteId(), classe.getEffectifMax());
    }
}
