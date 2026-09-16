package tg.novadigital.edukeys.academique.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.repository.CycleRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.CreerCycleRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierCycleRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserCode;
import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserLibelle;

/**
 * Cycle de vie d'un cycle (US-02, D1). Patron aligné sur
 * {@code AnneeScolaireService} (US-01) : vérification applicative puis filet
 * {@code DataIntegrityViolationException} (R3), flush avant fermeture de
 * chaque {@code PorteeEtablissement} (règle 12).
 */
@Service
public class CycleService {

    private final CycleRepository cycleRepository;
    private final NiveauRepository niveauRepository;
    private final EntityManager entityManager;

    public CycleService(CycleRepository cycleRepository, NiveauRepository niveauRepository, EntityManager entityManager) {
        this.cycleRepository = cycleRepository;
        this.niveauRepository = niveauRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Cycle> lister(boolean inclureInactifs) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return inclureInactifs
                ? cycleRepository.findByEtablissementIdOrderByRangAsc(etablissementId)
                : cycleRepository.findByEtablissementIdAndActifTrueOrderByRangAsc(etablissementId);
    }

    @Transactional(readOnly = true)
    public Cycle obtenir(UUID id) {
        return cycleRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.CYCLE_INTROUVABLE, "Cycle introuvable."));
    }

    @Transactional
    public Cycle creer(CreerCycleRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = validerLibelle(requete.libelle());
        String code = normaliserCode(requete.code());
        int rang = requete.rang();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            verifierLibelleDisponible(etablissementId, libelle);
            verifierRangDisponible(etablissementId, rang);
            verifierCodeDisponible(etablissementId, code);

            Cycle cycle = new Cycle(etablissementId, libelle, code, rang);
            try {
                Cycle sauve = cycleRepository.save(cycle);
                entityManager.flush();
                return sauve;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    @Transactional
    public Cycle modifier(UUID id, ModifierCycleRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = validerLibelle(requete.libelle());
        String code = normaliserCode(requete.code());
        int rang = requete.rang();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Cycle cycle = obtenir(id);

            if (!libelle.equals(cycle.getLibelle())) {
                verifierLibelleDisponible(etablissementId, libelle);
            }
            if (rang != cycle.getRang()) {
                verifierRangDisponible(etablissementId, rang);
            }
            if (code != null && !code.equals(cycle.getCode())) {
                verifierCodeDisponible(etablissementId, code);
            }

            cycle.modifier(libelle, code, rang);
            try {
                Cycle sauve = cycleRepository.save(cycle);
                entityManager.flush();
                return sauve;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    /** R13 : refus explicite si le cycle porte encore des niveaux actifs — pas de cascade. */
    @Transactional
    public void desactiver(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Cycle cycle = obtenir(id);
            long niveauxActifs = niveauRepository.countByEtablissementIdAndCycleIdAndActifTrue(etablissementId, id);
            if (niveauxActifs > 0) {
                throw new RegleMetierViolee(CodeErreur.CYCLE_NON_DESACTIVABLE,
                        "Ce cycle porte encore des niveaux actifs : désactivez-les d'abord.");
            }
            cycle.desactiver();
            cycleRepository.save(cycle);
            entityManager.flush();
        }
    }

    /** {@code @NotBlank} côté DTO garantit déjà un libellé non blanc ; {@code trim()} normalise l'espace superflu (R1). */
    private String validerLibelle(String libelleSaisi) {
        return normaliserLibelle(libelleSaisi);
    }

    private void verifierLibelleDisponible(UUID etablissementId, String libelle) {
        if (cycleRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, libelle)) {
            throw new ConflitException(CodeErreur.CYCLE_LIBELLE_DUPLIQUE, "Un cycle actif porte déjà ce libellé.");
        }
    }

    private void verifierRangDisponible(UUID etablissementId, int rang) {
        if (cycleRepository.existsByEtablissementIdAndRangAndActifTrue(etablissementId, rang)) {
            throw new ConflitException(CodeErreur.CYCLE_RANG_DUPLIQUE, "Un cycle actif porte déjà ce rang.");
        }
    }

    private void verifierCodeDisponible(UUID etablissementId, String code) {
        if (code != null && cycleRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, code)) {
            throw new ConflitException(CodeErreur.CYCLE_CODE_DUPLIQUE, "Un cycle actif porte déjà ce code.");
        }
    }

    private ConflitException traduireViolation(DataIntegrityViolationException e) {
        return new ConflitException(CodeErreur.CYCLE_LIBELLE_DUPLIQUE, "Un cycle actif porte déjà ce libellé, ce rang ou ce code.");
    }
}
