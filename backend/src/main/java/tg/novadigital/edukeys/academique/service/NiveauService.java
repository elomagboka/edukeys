package tg.novadigital.edukeys.academique.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.academique.repository.CycleRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.CreerNiveauRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierNiveauRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserCode;
import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserLibelle;

/** Cycle de vie d'un niveau (US-02), rattaché à un {@link Cycle} actif de l'établissement courant (R5). */
@Service
public class NiveauService {

    private final NiveauRepository niveauRepository;
    private final CycleRepository cycleRepository;
    private final ClasseRepository classeRepository;
    private final EntityManager entityManager;

    public NiveauService(NiveauRepository niveauRepository, CycleRepository cycleRepository,
                          ClasseRepository classeRepository, EntityManager entityManager) {
        this.niveauRepository = niveauRepository;
        this.cycleRepository = cycleRepository;
        this.classeRepository = classeRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Niveau> lister(UUID cycleId, boolean inclureInactifs) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        if (cycleId != null) {
            return inclureInactifs
                    ? niveauRepository.findByEtablissementIdAndCycleIdOrderByCycle_RangAscRangAsc(etablissementId, cycleId)
                    : niveauRepository.findByEtablissementIdAndCycleIdAndActifTrueOrderByCycle_RangAscRangAsc(etablissementId, cycleId);
        }
        return inclureInactifs
                ? niveauRepository.findByEtablissementIdOrderByCycle_RangAscRangAsc(etablissementId)
                : niveauRepository.findByEtablissementIdAndActifTrueOrderByCycle_RangAscRangAsc(etablissementId);
    }

    @Transactional(readOnly = true)
    public Niveau obtenir(UUID id) {
        return niveauRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.NIVEAU_INTROUVABLE, "Niveau introuvable."));
    }

    @Transactional
    public Niveau creer(CreerNiveauRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());
        String code = normaliserCode(requete.code());
        int rang = requete.rang();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Cycle cycle = obtenirCycleActif(requete.cycleId());
            verifierLibelleDisponible(etablissementId, libelle);
            verifierRangDisponible(etablissementId, rang);
            verifierCodeDisponible(etablissementId, code);

            Niveau niveau = new Niveau(etablissementId, libelle, code, rang, cycle);
            try {
                Niveau sauve = niveauRepository.save(niveau);
                entityManager.flush();
                return sauve;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    @Transactional
    public Niveau modifier(UUID id, ModifierNiveauRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());
        String code = normaliserCode(requete.code());
        int rang = requete.rang();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Niveau niveau = obtenir(id);
            Cycle cycle = obtenirCycleActif(requete.cycleId());

            if (!libelle.equals(niveau.getLibelle())) {
                verifierLibelleDisponible(etablissementId, libelle);
            }
            if (rang != niveau.getRang()) {
                verifierRangDisponible(etablissementId, rang);
            }
            if (code != null && !code.equals(niveau.getCode())) {
                verifierCodeDisponible(etablissementId, code);
            }

            niveau.modifier(libelle, code, rang, cycle);
            try {
                Niveau sauve = niveauRepository.save(niveau);
                entityManager.flush();
                return sauve;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    /** R13 : refus explicite si le niveau porte encore des classes actives (toutes années confondues). */
    @Transactional
    public void desactiver(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Niveau niveau = obtenir(id);
            long classesActives = classeRepository.countByEtablissementIdAndNiveauIdAndActifTrue(etablissementId, id);
            if (classesActives > 0) {
                throw new RegleMetierViolee(CodeErreur.NIVEAU_NON_DESACTIVABLE,
                        "Ce niveau porte encore des classes actives : désactivez-les d'abord.");
            }
            niveau.desactiver();
            niveauRepository.save(niveau);
            entityManager.flush();
        }
    }

    private Cycle obtenirCycleActif(UUID cycleId) {
        return cycleRepository.findById(cycleId)
                .filter(Cycle::isActif)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.CYCLE_INTROUVABLE, "Cycle introuvable ou inactif."));
    }

    private void verifierLibelleDisponible(UUID etablissementId, String libelle) {
        if (niveauRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, libelle)) {
            throw new ConflitException(CodeErreur.NIVEAU_LIBELLE_DUPLIQUE, "Un niveau actif porte déjà ce libellé.");
        }
    }

    private void verifierRangDisponible(UUID etablissementId, int rang) {
        if (niveauRepository.existsByEtablissementIdAndRangAndActifTrue(etablissementId, rang)) {
            throw new ConflitException(CodeErreur.NIVEAU_RANG_DUPLIQUE, "Un niveau actif porte déjà ce rang.");
        }
    }

    private void verifierCodeDisponible(UUID etablissementId, String code) {
        if (code != null && niveauRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, code)) {
            throw new ConflitException(CodeErreur.NIVEAU_CODE_DUPLIQUE, "Un niveau actif porte déjà ce code.");
        }
    }

    private ConflitException traduireViolation(DataIntegrityViolationException e) {
        return new ConflitException(CodeErreur.NIVEAU_LIBELLE_DUPLIQUE, "Un niveau actif porte déjà ce libellé, ce rang ou ce code.");
    }
}
