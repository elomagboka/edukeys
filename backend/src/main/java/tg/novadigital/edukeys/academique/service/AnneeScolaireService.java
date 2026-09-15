package tg.novadigital.edukeys.academique.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.AnneeScolaireQuery;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.repository.AnneeScolaireRepository;
import tg.novadigital.edukeys.academique.web.CreerAnneeScolaireRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierAnneeScolaireRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Cycle de vie d'une année scolaire (US-01). Implémente {@link AnneeScolaireQuery},
 * seul point d'entrée exposé aux autres modules (R14) — aucune méthode ici ne
 * laisse fuir l'entité {@link AnneeScolaire} en dehors du module (le
 * contrôleur mappe systématiquement vers un DTO, CLAUDE.md règle 7).
 */
@Service
public class AnneeScolaireService implements AnneeScolaireQuery {

    private static final int DUREE_MIN_JOURS = 30;
    private static final int DUREE_MAX_JOURS = 500;

    private final AnneeScolaireRepository anneeScolaireRepository;
    private final EntityManager entityManager;

    public AnneeScolaireService(AnneeScolaireRepository anneeScolaireRepository, EntityManager entityManager) {
        this.anneeScolaireRepository = anneeScolaireRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<AnneeScolaire> lister(StatutAnneeScolaire statut, boolean inclureInactives) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        if (statut != null) {
            return inclureInactives
                    ? anneeScolaireRepository.findByEtablissementIdAndStatutOrderByDateDebutDesc(etablissementId, statut)
                    : anneeScolaireRepository.findByEtablissementIdAndActifTrueAndStatutOrderByDateDebutDesc(etablissementId, statut);
        }
        return inclureInactives
                ? anneeScolaireRepository.findByEtablissementIdOrderByDateDebutDesc(etablissementId)
                : anneeScolaireRepository.findByEtablissementIdAndActifTrueOrderByDateDebutDesc(etablissementId);
    }

    @Transactional(readOnly = true)
    public AnneeScolaire obtenirActive() {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        CodeErreur.ANNEE_SCOLAIRE_ACTIVE_ABSENTE, "Aucune année scolaire active pour cet établissement."));
    }

    @Transactional(readOnly = true)
    public AnneeScolaire obtenir(UUID id) {
        return anneeScolaireRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        CodeErreur.ANNEE_SCOLAIRE_INTROUVABLE, "Année scolaire introuvable."));
    }

    @Transactional
    public AnneeScolaire creer(CreerAnneeScolaireRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        validerDates(requete.dateDebut(), requete.dateFin());
        String libelle = resoudreLibelle(requete.libelle(), requete.dateDebut(), requete.dateFin());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            verifierLibelleDisponible(etablissementId, libelle, null);
            verifierAucunChevauchement(etablissementId, requete.dateDebut(), requete.dateFin(), null);

            AnneeScolaire anneeScolaire = new AnneeScolaire(etablissementId, libelle, requete.dateDebut(), requete.dateFin());
            try {
                AnneeScolaire sauvee = anneeScolaireRepository.save(anneeScolaire);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolationChevauchement(e);
            }
        }
    }

    @Transactional
    public AnneeScolaire modifier(UUID id, ModifierAnneeScolaireRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        validerDates(requete.dateDebut(), requete.dateFin());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            AnneeScolaire anneeScolaire = obtenir(id);

            if (anneeScolaire.getStatut() == StatutAnneeScolaire.CLOTUREE) {
                throw new RegleMetierViolee(
                        CodeErreur.ANNEE_SCOLAIRE_CLOTUREE_IMMUABLE, "Une année scolaire clôturée est immuable.");
            }

            String libelle = requete.libelle() == null || requete.libelle().isBlank()
                    ? anneeScolaire.getLibelle()
                    : requete.libelle().trim();
            if (!libelle.equals(anneeScolaire.getLibelle())) {
                verifierLibelleDisponible(etablissementId, libelle, id);
            }
            verifierAucunChevauchement(etablissementId, requete.dateDebut(), requete.dateFin(), id);

            anneeScolaire.modifierDates(requete.dateDebut(), requete.dateFin(), libelle);
            try {
                AnneeScolaire sauvee = anneeScolaireRepository.save(anneeScolaire);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                throw traduireViolationChevauchement(e);
            }
        }
    }

    /** R8 : bascule transactionnelle — l'année ACTIVE en cours (s'il y en a une) passe à CLOTUREE, puis la cible passe à ACTIVE. */
    @Transactional
    public AnneeScolaire activer(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            AnneeScolaire cible = obtenir(id);
            if (cible.getStatut() != StatutAnneeScolaire.PREPARATION) {
                throw new RegleMetierViolee(CodeErreur.ANNEE_SCOLAIRE_TRANSITION_INVALIDE,
                        "Seule une année en préparation peut être activée.");
            }

            Instant maintenant = Instant.now();
            anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                    .filter(active -> !active.getId().equals(id))
                    .ifPresent(active -> {
                        active.cloturer(maintenant);
                        anneeScolaireRepository.save(active);
                        // Flush immédiat (point d'attention 3, règle 12) : l'index
                        // unique partiel uk_annees_scolaires_active interdit
                        // temporairement deux années ACTIVE. Sans ce flush,
                        // Hibernate peut ordonner les deux UPDATE dans un ordre
                        // qui viole la contrainte au flush suivant.
                        entityManager.flush();
                    });

            cible.activer(maintenant);
            try {
                AnneeScolaire sauvee = anneeScolaireRepository.save(cible);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException e) {
                // Concurrence (point d'attention 4) : deux activations simultanées
                // passent toutes deux la vérification applicative ci-dessus, puis
                // violent uk_annees_scolaires_active au flush.
                throw new ConflitException(CodeErreur.ANNEE_SCOLAIRE_ACTIVATION_CONCURRENTE,
                        "Une autre année scolaire a été activée entre-temps pour cet établissement.");
            }
        }
    }

    /** R10 : ACTIVE -> CLOTUREE. */
    @Transactional
    public AnneeScolaire cloturer(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            AnneeScolaire anneeScolaire = obtenir(id);
            if (anneeScolaire.getStatut() != StatutAnneeScolaire.ACTIVE) {
                throw new RegleMetierViolee(CodeErreur.ANNEE_SCOLAIRE_TRANSITION_INVALIDE,
                        "Seule une année active peut être clôturée.");
            }
            anneeScolaire.cloturer(Instant.now());
            AnneeScolaire sauvee = anneeScolaireRepository.save(anneeScolaire);
            entityManager.flush();
            return sauvee;
        }
    }

    /** R12 (A4) : désactivation permise uniquement en PREPARATION. */
    @Transactional
    public void desactiver(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            AnneeScolaire anneeScolaire = obtenir(id);
            if (anneeScolaire.getStatut() != StatutAnneeScolaire.PREPARATION) {
                throw new RegleMetierViolee(CodeErreur.ANNEE_SCOLAIRE_DESACTIVATION_REFUSEE,
                        "Seule une année en préparation peut être désactivée.");
            }
            anneeScolaire.desactiver();
            anneeScolaireRepository.save(anneeScolaire);
            entityManager.flush();
        }
    }

    // ------------------------------------------------------------------
    // AnneeScolaireQuery (R14) : seul point d'entrée pour les autres modules
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> idAnneeActive() {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return anneeScolaireRepository.findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                .map(AnneeScolaire::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estModifiable(UUID anneeId) {
        return anneeScolaireRepository.findById(anneeId)
                .map(annee -> annee.getStatut() != StatutAnneeScolaire.CLOTUREE)
                .orElse(false);
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    private void validerDates(LocalDate dateDebut, LocalDate dateFin) {
        if (!dateFin.isAfter(dateDebut)) {
            throw new RegleMetierViolee(CodeErreur.ANNEE_SCOLAIRE_DATES_INCOHERENTES,
                    "La date de fin doit être postérieure à la date de début.");
        }
        long dureeJours = ChronoUnit.DAYS.between(dateDebut, dateFin);
        if (dureeJours < DUREE_MIN_JOURS || dureeJours > DUREE_MAX_JOURS) {
            throw new RegleMetierViolee(CodeErreur.ANNEE_SCOLAIRE_DUREE_INVALIDE,
                    "La durée d'une année scolaire doit être comprise entre %d et %d jours."
                            .formatted(DUREE_MIN_JOURS, DUREE_MAX_JOURS));
        }
    }

    /** DELTA 1 (R3) : libellé absent ou blanc -> généré au format AAAA-AAAA ; fourni -> trim() seulement. */
    private String resoudreLibelle(String libelleSaisi, LocalDate dateDebut, LocalDate dateFin) {
        if (libelleSaisi == null || libelleSaisi.isBlank()) {
            return "%d-%d".formatted(dateDebut.getYear(), dateFin.getYear());
        }
        String libelle = libelleSaisi.trim();
        if (libelle.isEmpty()) {
            throw new RegleMetierViolee(CodeErreur.ANNEE_SCOLAIRE_LIBELLE_VIDE, "Le libellé ne peut pas être vide.");
        }
        return libelle;
    }

    private void verifierLibelleDisponible(UUID etablissementId, String libelle, UUID excludeId) {
        boolean existeDeja = excludeId == null
                ? anneeScolaireRepository.existsByEtablissementIdAndLibelleAndActifTrue(etablissementId, libelle)
                : anneeScolaireRepository.findByEtablissementIdAndActifTrueOrderByDateDebutDesc(etablissementId).stream()
                        .anyMatch(a -> !a.getId().equals(excludeId) && a.getLibelle().equals(libelle));
        if (existeDeja) {
            throw new ConflitException(CodeErreur.ANNEE_SCOLAIRE_LIBELLE_DUPLIQUE,
                    "Une année scolaire active porte déjà ce libellé.");
        }
    }

    private void verifierAucunChevauchement(UUID etablissementId, LocalDate dateDebut, LocalDate dateFin, UUID excludeId) {
        List<AnneeScolaire> chevauchements =
                anneeScolaireRepository.rechercherChevauchements(etablissementId, dateDebut, dateFin, excludeId);
        if (!chevauchements.isEmpty()) {
            throw new ConflitException(CodeErreur.ANNEE_SCOLAIRE_PERIODE_CHEVAUCHANTE,
                    "Cette période chevauche une autre année scolaire de l'établissement.");
        }
    }

    private ConflitException traduireViolationChevauchement(DataIntegrityViolationException e) {
        return new ConflitException(CodeErreur.ANNEE_SCOLAIRE_PERIODE_CHEVAUCHANTE,
                "Cette période chevauche une autre année scolaire de l'établissement.");
    }
}
