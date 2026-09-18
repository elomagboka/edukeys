package tg.novadigital.edukeys.academique.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.repository.AffectationMatiereRepository;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.academique.repository.CycleRepository;
import tg.novadigital.edukeys.academique.repository.FiliereRepository;
import tg.novadigital.edukeys.academique.web.CreerFiliereRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierFiliereRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserCode;
import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserLibelle;

/** Cycle de vie d'une filière (US-02), rattachement au cycle optionnel (D4, R6). */
@Service
public class FiliereService {

    private final FiliereRepository filiereRepository;
    private final CycleRepository cycleRepository;
    private final ClasseRepository classeRepository;
    private final AffectationMatiereRepository affectationMatiereRepository;
    private final EntityManager entityManager;

    public FiliereService(FiliereRepository filiereRepository, CycleRepository cycleRepository,
                           ClasseRepository classeRepository, AffectationMatiereRepository affectationMatiereRepository,
                           EntityManager entityManager) {
        this.filiereRepository = filiereRepository;
        this.cycleRepository = cycleRepository;
        this.classeRepository = classeRepository;
        this.affectationMatiereRepository = affectationMatiereRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Filiere> lister(UUID cycleId, boolean inclureInactives) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        if (cycleId != null) {
            return inclureInactives
                    ? filiereRepository.findByEtablissementIdAndCycleIdOrderByLibelleAsc(etablissementId, cycleId)
                    : filiereRepository.findByEtablissementIdAndCycleIdAndActifTrueOrderByLibelleAsc(etablissementId, cycleId);
        }
        return inclureInactives
                ? filiereRepository.findByEtablissementIdOrderByLibelleAsc(etablissementId)
                : filiereRepository.findByEtablissementIdAndActifTrueOrderByLibelleAsc(etablissementId);
    }

    @Transactional(readOnly = true)
    public Filiere obtenir(UUID id) {
        return filiereRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.FILIERE_INTROUVABLE, "Filière introuvable."));
    }

    @Transactional
    public Filiere creer(CreerFiliereRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());
        String code = normaliserCode(requete.code());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Cycle cycle = requete.cycleId() == null ? null : obtenirCycleActif(requete.cycleId());
            verifierLibelleDisponible(etablissementId, libelle);
            verifierCodeDisponible(etablissementId, code);

            Filiere filiere = new Filiere(etablissementId, libelle, code, cycle);
            try {
                Filiere sauvee = filiereRepository.save(filiere);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    @Transactional
    public Filiere modifier(UUID id, ModifierFiliereRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());
        String code = normaliserCode(requete.code());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Filiere filiere = obtenir(id);
            Cycle cycle = requete.cycleId() == null ? null : obtenirCycleActif(requete.cycleId());

            if (!libelle.equals(filiere.getLibelle())) {
                verifierLibelleDisponible(etablissementId, libelle);
            }
            if (code != null && !code.equals(filiere.getCode())) {
                verifierCodeDisponible(etablissementId, code);
            }

            filiere.modifier(libelle, code, cycle);
            try {
                Filiere sauvee = filiereRepository.save(filiere);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    /** R13 : refus explicite si la filière est référencée par des classes actives. */
    @Transactional
    public void desactiver(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Filiere filiere = obtenir(id);
            long classesActives = classeRepository.countByEtablissementIdAndFiliereIdAndActifTrue(etablissementId, id);
            if (classesActives > 0) {
                throw new RegleMetierViolee(CodeErreur.FILIERE_NON_DESACTIVABLE,
                        "Cette filière est encore référencée par des classes actives : désactivez-les d'abord.");
            }
            // R9 (US-03) : une filière encore référencée par une affectation de matière active ne peut pas être désactivée.
            long affectationsMatieresActives = affectationMatiereRepository
                    .countByEtablissementIdAndFiliereIdAndActifTrue(etablissementId, id);
            if (affectationsMatieresActives > 0) {
                throw new RegleMetierViolee(CodeErreur.FILIERE_NON_DESACTIVABLE,
                        "Cette filière est encore référencée par des affectations de matières actives : désactivez-les d'abord.");
            }
            filiere.desactiver();
            filiereRepository.save(filiere);
            entityManager.flush();
        }
    }

    private Cycle obtenirCycleActif(UUID cycleId) {
        return cycleRepository.findById(cycleId)
                .filter(Cycle::isActif)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.CYCLE_INTROUVABLE, "Cycle introuvable ou inactif."));
    }

    private void verifierLibelleDisponible(UUID etablissementId, String libelle) {
        if (filiereRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, libelle)) {
            throw new ConflitException(CodeErreur.FILIERE_LIBELLE_DUPLIQUE, "Une filière active porte déjà ce libellé.");
        }
    }

    private void verifierCodeDisponible(UUID etablissementId, String code) {
        if (code != null && filiereRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, code)) {
            throw new ConflitException(CodeErreur.FILIERE_CODE_DUPLIQUE, "Une filière active porte déjà ce code.");
        }
    }

    /**
     * Discrimine sur le nom de la contrainte violée (point IMPORTANT n°3 de la
     * revue US-02), sur le même principe que {@code CycleService}. Contrainte
     * inconnue ou absente : l'exception d'origine remonte telle quelle, sans
     * deviner de code.
     */
    private RuntimeException traduireViolation(DataIntegrityViolationException e) {
        String contrainte = UtilitairesAcademique.nomContrainteViolee(e);
        if (contrainte == null) {
            return e;
        }
        return switch (contrainte) {
            case "uk_filieres_libelle_actif" -> new ConflitException(
                    CodeErreur.FILIERE_LIBELLE_DUPLIQUE, "Une filière active porte déjà ce libellé.");
            case "uk_filieres_code_actif" -> new ConflitException(
                    CodeErreur.FILIERE_CODE_DUPLIQUE, "Une filière active porte déjà ce code.");
            default -> e;
        };
    }
}
