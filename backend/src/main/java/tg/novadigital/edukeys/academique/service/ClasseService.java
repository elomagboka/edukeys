package tg.novadigital.edukeys.academique.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.repository.AnneeScolaireRepository;
import tg.novadigital.edukeys.academique.repository.ClasseRepository;
import tg.novadigital.edukeys.academique.repository.FiliereRepository;
import tg.novadigital.edukeys.academique.repository.NiveauRepository;
import tg.novadigital.edukeys.academique.web.CreerClasseRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierClasseRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.etablissement.SiteQuery;

import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserLibelle;

/**
 * Cycle de vie d'une classe (US-02, D2/D3). Chaque classe est rattachée à une
 * {@link AnneeScolaire} obligatoire, jamais modifiée après création (R8/R12).
 * {@code siteId} est le seul identifiant de requête que le filtre Hibernate
 * ne protège pas : il n'est <strong>jamais</strong> fait confiance sans
 * passer par {@link SiteQuery#existeDansEtablissementCourant(UUID)} (R11,
 * point le plus sensible de cette US).
 */
@Service
public class ClasseService {

    private final ClasseRepository classeRepository;
    private final NiveauRepository niveauRepository;
    private final FiliereRepository filiereRepository;
    private final AnneeScolaireRepository anneeScolaireRepository;
    private final SiteQuery siteQuery;
    private final EntityManager entityManager;

    public ClasseService(ClasseRepository classeRepository, NiveauRepository niveauRepository,
                          FiliereRepository filiereRepository, AnneeScolaireRepository anneeScolaireRepository,
                          SiteQuery siteQuery, EntityManager entityManager) {
        this.classeRepository = classeRepository;
        this.niveauRepository = niveauRepository;
        this.filiereRepository = filiereRepository;
        this.anneeScolaireRepository = anneeScolaireRepository;
        this.siteQuery = siteQuery;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Classe> lister(UUID anneeScolaireId, UUID niveauId, UUID filiereId, UUID siteId, boolean inclureInactives) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        UUID anneeResolue = anneeScolaireId;
        if (anneeResolue == null) {
            // Spec §6 : pas d'année active -> liste vide, jamais 404 (établissement en cours de paramétrage).
            anneeResolue = anneeScolaireRepository
                    .findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                    .map(AnneeScolaire::getId)
                    .orElse(null);
            if (anneeResolue == null) {
                return List.of();
            }
        }
        return classeRepository.rechercher(etablissementId, anneeResolue, niveauId, filiereId, siteId, inclureInactives);
    }

    @Transactional(readOnly = true)
    public Classe obtenir(UUID id) {
        return classeRepository.findWithGraphById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.CLASSE_INTROUVABLE, "Classe introuvable."));
    }

    @Transactional
    public Classe creer(CreerClasseRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String suffixe = normaliserLibelle(requete.suffixe());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Niveau niveau = obtenirNiveauActif(requete.niveauId());
            Filiere filiere = requete.filiereId() == null ? null : obtenirFiliereActive(requete.filiereId());
            AnneeScolaire anneeScolaire = resoudreAnneeScolaireModifiable(etablissementId, requete.anneeScolaireId());
            UUID siteId = resoudreSite(requete.siteId());
            verifierCoherenceFiliereCycle(niveau, filiere);

            String libelle = resoudreLibelle(requete.libelle(), niveau, suffixe);
            verifierLibelleDisponible(etablissementId, anneeScolaire.getId(), libelle, null);

            Classe classe = new Classe(etablissementId, libelle, suffixe, niveau, filiere, anneeScolaire, siteId,
                    requete.effectifMax());
            try {
                Classe sauvee = classeRepository.save(classe);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    @Transactional
    public Classe modifier(UUID id, ModifierClasseRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String suffixe = normaliserLibelle(requete.suffixe());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Classe classe = obtenir(id);
            verifierAnneeModifiable(classe);

            Niveau niveau = obtenirNiveauActif(requete.niveauId());
            Filiere filiere = requete.filiereId() == null ? null : obtenirFiliereActive(requete.filiereId());
            UUID siteId = requete.siteId() == null ? classe.getSiteId() : resoudreSite(requete.siteId());
            verifierCoherenceFiliereCycle(niveau, filiere);

            String libelle = resoudreLibelle(requete.libelle(), niveau, suffixe);
            if (!libelle.equals(classe.getLibelle())) {
                verifierLibelleDisponible(etablissementId, classe.getAnneeScolaire().getId(), libelle, id);
            }

            classe.modifier(libelle, suffixe, niveau, filiere, siteId, requete.effectifMax());
            try {
                Classe sauvee = classeRepository.save(classe);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    /** R12/R14 : désactivation logique uniquement, refusée si l'année est clôturée. */
    @Transactional
    public void desactiver(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Classe classe = obtenir(id);
            verifierAnneeModifiable(classe);
            classe.desactiver();
            classeRepository.save(classe);
            entityManager.flush();
        }
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    private Niveau obtenirNiveauActif(UUID niveauId) {
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.NIVEAU_INTROUVABLE, "Niveau introuvable."));
        if (!niveau.isActif()) {
            throw new RegleMetierViolee(CodeErreur.CLASSE_REFERENTIEL_INACTIF, "Ce niveau est désactivé.");
        }
        return niveau;
    }

    private Filiere obtenirFiliereActive(UUID filiereId) {
        Filiere filiere = filiereRepository.findById(filiereId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.FILIERE_INTROUVABLE, "Filière introuvable."));
        if (!filiere.isActif()) {
            throw new RegleMetierViolee(CodeErreur.CLASSE_REFERENTIEL_INACTIF, "Cette filière est désactivée.");
        }
        return filiere;
    }

    /** R8 : annee fournie ou année active par défaut, doit exister et ne pas être clôturée. */
    private AnneeScolaire resoudreAnneeScolaireModifiable(UUID etablissementId, UUID anneeScolaireId) {
        AnneeScolaire anneeScolaire = anneeScolaireId != null
                ? anneeScolaireRepository.findById(anneeScolaireId)
                        .orElseThrow(() -> new RessourceIntrouvableException(
                                CodeErreur.ANNEE_SCOLAIRE_INTROUVABLE, "Année scolaire introuvable."))
                : anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                        .orElseThrow(() -> new RessourceIntrouvableException(
                                CodeErreur.ANNEE_SCOLAIRE_ACTIVE_ABSENTE, "Aucune année scolaire active pour cet établissement."));
        if (anneeScolaire.getStatut() == StatutAnneeScolaire.CLOTUREE) {
            throw new RegleMetierViolee(CodeErreur.CLASSE_ANNEE_CLOTUREE, "Cette année scolaire est clôturée.");
        }
        return anneeScolaire;
    }

    private void verifierAnneeModifiable(Classe classe) {
        if (classe.getAnneeScolaire().getStatut() == StatutAnneeScolaire.CLOTUREE) {
            throw new RegleMetierViolee(CodeErreur.CLASSE_ANNEE_CLOTUREE,
                    "Une classe dont l'année scolaire est clôturée est immuable.");
        }
    }

    /**
     * R11, point le plus sensible de l'US : {@code siteId} du corps de
     * requête n'est jamais fait confiance directement — toujours vérifié via
     * {@link SiteQuery}, seul rempart contre un site appartenant à un autre
     * établissement.
     */
    private UUID resoudreSite(UUID siteId) {
        if (siteId == null) {
            return siteQuery.idSitePrincipal();
        }
        if (!siteQuery.existeDansEtablissementCourant(siteId)) {
            throw new RegleMetierViolee(CodeErreur.CLASSE_SITE_INVALIDE,
                    "Ce site n'appartient pas à l'établissement courant.");
        }
        return siteId;
    }

    /** R10 : si la filière porte un cycle, il doit être celui du niveau de la classe. */
    private void verifierCoherenceFiliereCycle(Niveau niveau, Filiere filiere) {
        if (filiere != null && filiere.getCycle() != null
                && !filiere.getCycle().getId().equals(niveau.getCycle().getId())) {
            throw new RegleMetierViolee(CodeErreur.FILIERE_CYCLE_INCOHERENT,
                    "Cette filière n'appartient pas au cycle du niveau de la classe.");
        }
    }

    /** D5 : libellé libre ; absent ou blanc -> composé depuis {@code niveau.libelle + " " + suffixe}. */
    private String resoudreLibelle(String libelleSaisi, Niveau niveau, String suffixe) {
        String libelle = normaliserLibelle(libelleSaisi);
        if (libelle != null && !libelle.isEmpty()) {
            return libelle;
        }
        String compose = suffixe == null || suffixe.isEmpty()
                ? niveau.getLibelle()
                : niveau.getLibelle() + " " + suffixe;
        if (compose.isBlank()) {
            throw new RegleMetierViolee(CodeErreur.CLASSE_LIBELLE_VIDE, "Le libellé ne peut pas être vide.");
        }
        return compose;
    }

    private void verifierLibelleDisponible(UUID etablissementId, UUID anneeScolaireId, String libelle, UUID excludeId) {
        boolean existeDeja = excludeId == null
                ? classeRepository.existsByEtablissementIdAndAnneeScolaireIdAndLibelleAndActifTrue(
                        etablissementId, anneeScolaireId, libelle)
                : classeRepository.rechercher(etablissementId, anneeScolaireId, null, null, null, false).stream()
                        .anyMatch(c -> !c.getId().equals(excludeId) && c.getLibelle().equals(libelle));
        if (existeDeja) {
            throw new ConflitException(CodeErreur.CLASSE_LIBELLE_DUPLIQUE,
                    "Une classe active porte déjà ce libellé pour cette année scolaire.");
        }
    }

    private ConflitException traduireViolation(DataIntegrityViolationException e) {
        return new ConflitException(CodeErreur.CLASSE_LIBELLE_DUPLIQUE,
                "Une classe active porte déjà ce libellé pour cette année scolaire.");
    }
}
