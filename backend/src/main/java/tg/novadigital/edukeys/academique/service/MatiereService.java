package tg.novadigital.edukeys.academique.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AffectationMatiere;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.domain.Matiere;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.repository.AffectationMatiereRepository;
import tg.novadigital.edukeys.academique.repository.FiliereRepository;
import tg.novadigital.edukeys.academique.repository.MatiereRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.AffectationMatiereRequestDto;
import tg.novadigital.edukeys.academique.web.CreerMatiereRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierMatiereRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserCode;
import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserLibelle;

/**
 * Cycle de vie d'une matière et de ses affectations (niveau, filière
 * optionnelle) — US-03. Une matière existe indépendamment de ses affectations
 * (critère d'acceptation) : {@link #creer} accepte une liste vide ou absente.
 *
 * <p>Les affectations sont permanentes, non versionnées par année scolaire —
 * voir la javadoc de {@link AffectationMatiere} pour la conséquence sur
 * US-20 (bulletins).</p>
 */
@Service
public class MatiereService {

    private final MatiereRepository matiereRepository;
    private final AffectationMatiereRepository affectationMatiereRepository;
    private final NiveauRepository niveauRepository;
    private final FiliereRepository filiereRepository;
    private final EntityManager entityManager;

    public MatiereService(MatiereRepository matiereRepository, AffectationMatiereRepository affectationMatiereRepository,
                           NiveauRepository niveauRepository, FiliereRepository filiereRepository,
                           EntityManager entityManager) {
        this.matiereRepository = matiereRepository;
        this.affectationMatiereRepository = affectationMatiereRepository;
        this.niveauRepository = niveauRepository;
        this.filiereRepository = filiereRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Matiere> lister(UUID niveauId, UUID filiereId, boolean inclureInactives) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        if (niveauId != null) {
            return matiereRepository.findAffecteesAuNiveau(etablissementId, niveauId, inclureInactives);
        }
        if (filiereId != null) {
            return matiereRepository.findAffecteesALaFiliere(etablissementId, filiereId, inclureInactives);
        }
        return inclureInactives
                ? matiereRepository.findByEtablissementIdOrderByLibelleAsc(etablissementId)
                : matiereRepository.findByEtablissementIdAndActifTrueOrderByLibelleAsc(etablissementId);
    }

    @Transactional(readOnly = true)
    public Matiere obtenir(UUID id) {
        return matiereRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.MATIERE_INTROUVABLE, "Matière introuvable."));
    }

    /**
     * Anti N+1 (point bloquant de la spec) : une seule requête pour
     * l'ensemble des matières demandées, quel que soit leur nombre.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<AffectationMatiere>> affectationsActivesParMatiere(List<UUID> matiereIds) {
        if (matiereIds.isEmpty()) {
            return Map.of();
        }
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return affectationMatiereRepository.findByEtablissementIdAndMatiereIdInAndActifTrue(etablissementId, matiereIds).stream()
                .collect(Collectors.groupingBy(a -> a.getMatiere().getId()));
    }

    @Transactional(readOnly = true)
    public List<AffectationMatiere> affectationsActives(UUID matiereId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return affectationMatiereRepository.findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, matiereId);
    }

    @Transactional
    public Matiere creer(CreerMatiereRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());
        String code = normaliserCode(requete.code());
        List<AffectationMatiereRequestDto> affectations = requete.affectations() == null ? List.of() : requete.affectations();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            verifierLibelleDisponible(etablissementId, libelle);
            verifierCodeDisponible(etablissementId, code);

            Matiere matiere = new Matiere(etablissementId, libelle, code);
            try {
                matiere = matiereRepository.save(matiere);
                entityManager.flush();
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }

            appliquerAffectations(etablissementId, matiere, affectations);
            entityManager.flush();
            return matiere;
        }
    }

    @Transactional
    public Matiere modifier(UUID id, ModifierMatiereRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());
        String code = normaliserCode(requete.code());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Matiere matiere = obtenir(id);

            if (!libelle.equals(matiere.getLibelle())) {
                verifierLibelleDisponible(etablissementId, libelle);
            }
            if (code != null && !code.equals(matiere.getCode())) {
                verifierCodeDisponible(etablissementId, code);
            }

            matiere.modifier(libelle, code);
            try {
                Matiere sauvee = matiereRepository.save(matiere);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    /**
     * R3 : remplace l'ensemble des affectations actives par l'ensemble cible,
     * en diffant sur la clé (niveauId, filiereId|null) — idempotent.
     */
    @Transactional
    public Matiere definirAffectations(UUID id, List<AffectationMatiereRequestDto> affectations) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Matiere matiere = obtenir(id);
            appliquerAffectations(etablissementId, matiere, affectations == null ? List.of() : affectations);
            entityManager.flush();
            return matiere;
        }
    }

    /** R8 : désactive la matière ET ses affectations actives, dans la même transaction. */
    @Transactional
    public void desactiver(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Matiere matiere = obtenir(id);
            matiere.desactiver();
            matiereRepository.save(matiere);

            List<AffectationMatiere> actives = affectationMatiereRepository
                    .findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, id);
            actives.forEach(a -> {
                a.desactiver();
                affectationMatiereRepository.save(a);
            });
            entityManager.flush();
        }
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    private record CleAffectation(UUID niveauId, UUID filiereId) {
    }

    /**
     * R3/R4/R5/R6/R7 : valide puis applique la cible demandée par diff avec
     * l'ensemble actif actuel. Ne crée jamais une nouvelle ligne pour une clé
     * déjà active : seuls ses attributs sont mis à jour, et seulement s'ils
     * changent (idempotence).
     */
    private void appliquerAffectations(UUID etablissementId, Matiere matiere, List<AffectationMatiereRequestDto> cibles) {
        if (!matiere.isActif() && !cibles.isEmpty()) {
            throw new RegleMetierViolee(CodeErreur.MATIERE_INACTIVE, "Cette matière est désactivée.");
        }

        Map<CleAffectation, AffectationMatiereRequestDto> parCle = new HashMap<>();
        for (AffectationMatiereRequestDto cible : cibles) {
            CleAffectation cle = new CleAffectation(cible.niveauId(), cible.filiereId());
            if (parCle.put(cle, cible) != null) {
                throw new RegleMetierViolee(CodeErreur.MATIERE_AFFECTATION_CLE_DUPLIQUEE,
                        "La combinaison niveau/filière est répétée dans la requête.");
            }
        }

        verifierAbsenceDeMelangeSurUnNiveau(cibles);

        Map<UUID, Niveau> niveaux = new HashMap<>();
        Map<UUID, Filiere> filieres = new HashMap<>();
        for (AffectationMatiereRequestDto cible : cibles) {
            Niveau niveau = niveaux.computeIfAbsent(cible.niveauId(), this::obtenirNiveauActif);
            if (cible.filiereId() != null) {
                Filiere filiere = filieres.computeIfAbsent(cible.filiereId(), this::obtenirFiliereActive);
                verifierCoherenceCycle(niveau, filiere);
            }
        }

        List<AffectationMatiere> existantes = affectationMatiereRepository
                .findByEtablissementIdAndMatiereIdAndActifTrue(etablissementId, matiere.getId());
        Map<CleAffectation, AffectationMatiere> existantesParCle = existantes.stream()
                .collect(Collectors.toMap(
                        a -> new CleAffectation(a.getNiveau().getId(), a.getFiliere() != null ? a.getFiliere().getId() : null),
                        a -> a));

        Set<CleAffectation> clesCibles = parCle.keySet();

        // Clés absentes de la cible -> désactivation.
        for (Map.Entry<CleAffectation, AffectationMatiere> entree : existantesParCle.entrySet()) {
            if (!clesCibles.contains(entree.getKey())) {
                entree.getValue().desactiver();
                affectationMatiereRepository.save(entree.getValue());
            }
        }

        // Clés cibles : mise à jour en place si déjà actives, création sinon.
        for (Map.Entry<CleAffectation, AffectationMatiereRequestDto> entree : parCle.entrySet()) {
            AffectationMatiereRequestDto cible = entree.getValue();
            AffectationMatiere existante = existantesParCle.get(entree.getKey());
            if (existante != null) {
                if (attributsDifferents(existante, cible)) {
                    existante.modifierAttributs(cible.coefficient(), cible.volumeHoraire(), cible.obligatoire());
                    affectationMatiereRepository.save(existante);
                }
            } else {
                Niveau niveau = niveaux.get(cible.niveauId());
                Filiere filiere = cible.filiereId() == null ? null : filieres.get(cible.filiereId());
                AffectationMatiere nouvelle = new AffectationMatiere(etablissementId, matiere, niveau, filiere,
                        cible.coefficient(), cible.volumeHoraire(), cible.obligatoire());
                try {
                    affectationMatiereRepository.save(nouvelle);
                    entityManager.flush();
                } catch (DataIntegrityViolationException e) {
                    throw traduireViolationAffectation(e);
                }
            }
        }
    }

    /** R5 : pour un même niveau, interdit de mélanger une affectation « niveau seul » et « niveau + filière ». */
    private void verifierAbsenceDeMelangeSurUnNiveau(List<AffectationMatiereRequestDto> cibles) {
        Map<UUID, Set<Boolean>> filiereRenseigneeParNiveau = new HashMap<>();
        for (AffectationMatiereRequestDto cible : cibles) {
            Set<Boolean> presences = filiereRenseigneeParNiveau.computeIfAbsent(cible.niveauId(), n -> new HashSet<>());
            presences.add(cible.filiereId() != null);
            if (presences.size() > 1) {
                throw new RegleMetierViolee(CodeErreur.MATIERE_AFFECTATION_INCOHERENTE,
                        "Un même niveau ne peut pas mélanger une affectation sans filière et une affectation avec filière.");
            }
        }
    }

    private boolean attributsDifferents(AffectationMatiere existante, AffectationMatiereRequestDto cible) {
        return coefficientDifferent(existante.getCoefficient(), cible.coefficient())
                || coefficientDifferent(existante.getVolumeHoraire(), cible.volumeHoraire())
                || !Objects.equals(existante.getObligatoire(), cible.obligatoire());
    }

    private boolean coefficientDifferent(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.compareTo(b) != 0;
    }

    private Niveau obtenirNiveauActif(UUID niveauId) {
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.NIVEAU_INTROUVABLE, "Niveau introuvable."));
        if (!niveau.isActif()) {
            throw new RessourceIntrouvableException(CodeErreur.NIVEAU_INTROUVABLE, "Niveau introuvable ou inactif.");
        }
        return niveau;
    }

    private Filiere obtenirFiliereActive(UUID filiereId) {
        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.FILIERE_INTROUVABLE, "Filière introuvable."));
        if (!filiere.isActif()) {
            throw new RessourceIntrouvableException(CodeErreur.FILIERE_INTROUVABLE, "Filière introuvable ou inactive.");
        }
        return filiere;
    }

    /** R6 : la filière d'une affectation doit appartenir au cycle du niveau affecté (même contrôle que R10 en US-02). */
    private void verifierCoherenceCycle(Niveau niveau, Filiere filiere) {
        if (filiere.getCycle() != null && !filiere.getCycle().getId().equals(niveau.getCycle().getId())) {
            throw new RegleMetierViolee(CodeErreur.FILIERE_CYCLE_INCOHERENT,
                    "Cette filière n'appartient pas au cycle du niveau affecté.");
        }
    }

    private void verifierLibelleDisponible(UUID etablissementId, String libelle) {
        if (matiereRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, libelle)) {
            throw new ConflitException(CodeErreur.MATIERE_LIBELLE_DUPLIQUE, "Une matière active porte déjà ce libellé.");
        }
    }

    private void verifierCodeDisponible(UUID etablissementId, String code) {
        if (code != null && matiereRepository.existsByEtablissementIdAndCodeAndActifTrue(etablissementId, code)) {
            throw new ConflitException(CodeErreur.MATIERE_CODE_DUPLIQUE, "Une matière active porte déjà ce code.");
        }
    }

    /**
     * Discrimine sur le nom de la contrainte violée (même principe que
     * {@code FiliereService}/{@code NiveauService}, US-02). Contrainte
     * inconnue ou absente : l'exception d'origine remonte telle quelle.
     */
    private RuntimeException traduireViolation(DataIntegrityViolationException e) {
        String contrainte = UtilitairesAcademique.nomContrainteViolee(e);
        if (contrainte == null) {
            return e;
        }
        return switch (contrainte) {
            case "uk_matieres_libelle_actif" -> new ConflitException(
                    CodeErreur.MATIERE_LIBELLE_DUPLIQUE, "Une matière active porte déjà ce libellé.");
            case "uk_matieres_code_actif" -> new ConflitException(
                    CodeErreur.MATIERE_CODE_DUPLIQUE, "Une matière active porte déjà ce code.");
            default -> e;
        };
    }

    private RuntimeException traduireViolationAffectation(DataIntegrityViolationException e) {
        String contrainte = UtilitairesAcademique.nomContrainteViolee(e);
        if (contrainte == null) {
            return e;
        }
        return switch (contrainte) {
            case "uk_affectations_matieres_actif", "uk_affectations_matieres_niveau_actif" ->
                    new ConflitException(CodeErreur.MATIERE_AFFECTATION_CLE_DUPLIQUEE,
                            "Cette combinaison niveau/filière est déjà affectée à cette matière.");
            default -> e;
        };
    }
}
