package tg.novadigital.edukeys.academique.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.PeriodeAcademique;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.academique.repository.AnneeScolaireRepository;
import tg.novadigital.edukeys.academique.repository.PeriodeAcademiqueRepository;
import tg.novadigital.edukeys.academique.web.CreerPeriodeAcademiqueRequestDto;
import tg.novadigital.edukeys.academique.web.ModifierPeriodeAcademiqueRequestDto;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

import static tg.novadigital.edukeys.academique.service.UtilitairesAcademique.normaliserLibelle;

/**
 * Cycle de vie d'une période académique (trimestre ou semestre) — US-05.
 *
 * <p><strong>Trous autorisés entre périodes</strong> (vacances de Noël,
 * etc.) : aucune contrainte de couverture continue de l'année n'est vérifiée
 * ici, seul le non-chevauchement l'est (R4, contrainte d'exclusion SQL). Le
 * rejet d'une note/absence hors période sera traité en US-15/US-18.</p>
 */
@Service
public class PeriodeAcademiqueService {

    private static final int DUREE_MIN_JOURS = 21;
    private static final int DUREE_MAX_JOURS = 250;

    private final PeriodeAcademiqueRepository periodeAcademiqueRepository;
    private final AnneeScolaireRepository anneeScolaireRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public PeriodeAcademiqueService(PeriodeAcademiqueRepository periodeAcademiqueRepository,
                                     AnneeScolaireRepository anneeScolaireRepository,
                                     EntityManager entityManager,
                                     Clock clock) {
        this.periodeAcademiqueRepository = periodeAcademiqueRepository;
        this.anneeScolaireRepository = anneeScolaireRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PeriodeAcademique> lister(UUID anneeScolaireId, boolean inclureInactives) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        if (anneeScolaireId != null) {
            return inclureInactives
                    ? periodeAcademiqueRepository.findByEtablissementIdAndAnneeScolaireIdOrderByOrdreAsc(etablissementId, anneeScolaireId)
                    : periodeAcademiqueRepository.findByEtablissementIdAndAnneeScolaireIdAndActifTrueOrderByOrdreAsc(
                            etablissementId, anneeScolaireId);
        }
        return inclureInactives
                ? periodeAcademiqueRepository.findByEtablissementIdOrderByOrdreAsc(etablissementId)
                : periodeAcademiqueRepository.findByEtablissementIdAndActifTrueOrderByOrdreAsc(etablissementId);
    }

    /**
     * Anti N+1 (spec, listing) : une seule requête pour l'ensemble des
     * libellés d'années scolaires distinctes présentes dans le listing.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> libellesAnneesScolaires(List<UUID> anneeScolaireIds) {
        if (anneeScolaireIds.isEmpty()) {
            return Map.of();
        }
        return anneeScolaireRepository.findByIdIn(anneeScolaireIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(AnneeScolaire::getId, AnneeScolaire::getLibelle));
    }

    @Transactional(readOnly = true)
    public PeriodeAcademique obtenir(UUID id) {
        return periodeAcademiqueRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        CodeErreur.PERIODE_ACADEMIQUE_INTROUVABLE, "Période académique introuvable."));
    }

    /** R7 : « en cours » = période active dont la plage inclut aujourd'hui, au sein de l'année ACTIVE. */
    @Transactional(readOnly = true)
    public PeriodeAcademique obtenirEnCours() {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        UUID anneeActiveId = anneeScolaireRepository
                .findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                .map(AnneeScolaire::getId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        CodeErreur.PERIODE_ACADEMIQUE_EN_COURS_ABSENTE, "Aucune année scolaire active pour cet établissement."));
        LocalDate aujourdHui = LocalDate.now(clock);
        return periodeAcademiqueRepository.trouverEnCours(etablissementId, anneeActiveId, aujourdHui)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        CodeErreur.PERIODE_ACADEMIQUE_EN_COURS_ABSENTE, "Aucune période académique en cours."));
    }

    /** R7 : dérivé à la lecture, jamais persisté. */
    @Transactional(readOnly = true)
    public boolean estEnCours(PeriodeAcademique periode) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        if (!periode.isActif()) {
            return false;
        }
        Optional<UUID> anneeActiveId = anneeScolaireRepository
                .findByEtablissementIdAndStatutAndActifTrue(etablissementId, StatutAnneeScolaire.ACTIVE)
                .map(AnneeScolaire::getId);
        if (anneeActiveId.isEmpty() || !anneeActiveId.get().equals(periode.getAnneeScolaireId())) {
            return false;
        }
        LocalDate aujourdHui = LocalDate.now(clock);
        return !periode.getDateDebut().isAfter(aujourdHui) && !periode.getDateFin().isBefore(aujourdHui);
    }

    @Transactional
    public PeriodeAcademique creer(CreerPeriodeAcademiqueRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            AnneeScolaire anneeScolaire = obtenirAnneeScolaire(requete.anneeScolaireId());
            verifierAnneeModifiable(anneeScolaire);
            validerDates(requete.dateDebut(), requete.dateFin(), anneeScolaire);
            verifierLibelleDisponible(etablissementId, anneeScolaire.getId(), libelle);
            verifierOrdreDisponible(etablissementId, anneeScolaire.getId(), requete.ordre());

            PeriodeAcademique periode = new PeriodeAcademique(etablissementId, anneeScolaire.getId(), libelle,
                    requete.type(), requete.ordre(), requete.dateDebut(), requete.dateFin());
            try {
                PeriodeAcademique sauvee = periodeAcademiqueRepository.save(periode);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    @Transactional
    public PeriodeAcademique modifier(UUID id, ModifierPeriodeAcademiqueRequestDto requete) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String libelle = normaliserLibelle(requete.libelle());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            PeriodeAcademique periode = obtenir(id);
            AnneeScolaire anneeScolaire = obtenirAnneeScolaire(periode.getAnneeScolaireId());
            verifierAnneeModifiable(anneeScolaire);
            validerDates(requete.dateDebut(), requete.dateFin(), anneeScolaire);

            if (!libelle.equals(periode.getLibelle())) {
                verifierLibelleDisponible(etablissementId, periode.getAnneeScolaireId(), libelle);
            }
            if (requete.ordre() != periode.getOrdre()) {
                verifierOrdreDisponible(etablissementId, periode.getAnneeScolaireId(), requete.ordre());
            }

            periode.modifier(libelle, requete.dateDebut(), requete.dateFin(), requete.ordre());
            try {
                PeriodeAcademique sauvee = periodeAcademiqueRepository.save(periode);
                entityManager.flush();
                return sauvee;
            } catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException e) {
                throw traduireViolation(e);
            }
        }
    }

    /** R8 : désactivation refusée si l'année est CLOTUREE. R10 : désactivation logique seule. */
    @Transactional
    public void desactiver(UUID id) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            PeriodeAcademique periode = obtenir(id);
            AnneeScolaire anneeScolaire = obtenirAnneeScolaire(periode.getAnneeScolaireId());
            verifierAnneeModifiable(anneeScolaire);
            periode.desactiver();
            periodeAcademiqueRepository.save(periode);
            entityManager.flush();
        }
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    private AnneeScolaire obtenirAnneeScolaire(UUID anneeScolaireId) {
        return anneeScolaireRepository.findById(anneeScolaireId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        CodeErreur.PERIODE_ACADEMIQUE_ANNEE_SCOLAIRE_INTROUVABLE, "Année scolaire introuvable."));
    }

    /** R8 : création/modification/désactivation refusées si l'année est CLOTUREE. */
    private void verifierAnneeModifiable(AnneeScolaire anneeScolaire) {
        if (anneeScolaire.getStatut() == StatutAnneeScolaire.CLOTUREE) {
            throw new RegleMetierViolee(CodeErreur.PERIODE_ACADEMIQUE_ANNEE_CLOTUREE,
                    "Une année scolaire clôturée n'accepte plus de modification de ses périodes académiques.");
        }
    }

    /** R1/R2/R3 : dates cohérentes, durée entre 21 et 250 jours, incluses dans les bornes de l'année (bornes comprises). */
    private void validerDates(LocalDate dateDebut, LocalDate dateFin, AnneeScolaire anneeScolaire) {
        if (!dateFin.isAfter(dateDebut)) {
            throw new RegleMetierViolee(CodeErreur.PERIODE_ACADEMIQUE_DATES_INCOHERENTES,
                    "La date de fin doit être postérieure à la date de début.");
        }
        long dureeJours = ChronoUnit.DAYS.between(dateDebut, dateFin);
        if (dureeJours < DUREE_MIN_JOURS || dureeJours > DUREE_MAX_JOURS) {
            throw new RegleMetierViolee(CodeErreur.PERIODE_ACADEMIQUE_DUREE_INVALIDE,
                    "La durée d'une période académique doit être comprise entre %d et %d jours."
                            .formatted(DUREE_MIN_JOURS, DUREE_MAX_JOURS));
        }
        if (dateDebut.isBefore(anneeScolaire.getDateDebut()) || dateFin.isAfter(anneeScolaire.getDateFin())) {
            throw new RegleMetierViolee(CodeErreur.PERIODE_ACADEMIQUE_HORS_BORNES_ANNEE,
                    "La période doit être comprise dans les bornes de l'année scolaire.");
        }
    }

    private void verifierLibelleDisponible(UUID etablissementId, UUID anneeScolaireId, String libelle) {
        if (periodeAcademiqueRepository.existsByEtablissementIdAndAnneeScolaireIdAndLibelleAndActifTrue(
                etablissementId, anneeScolaireId, libelle)) {
            throw new ConflitException(CodeErreur.PERIODE_ACADEMIQUE_LIBELLE_DUPLIQUE,
                    "Une période active de cette année scolaire porte déjà ce libellé.");
        }
    }

    private void verifierOrdreDisponible(UUID etablissementId, UUID anneeScolaireId, int ordre) {
        if (periodeAcademiqueRepository.existsByEtablissementIdAndAnneeScolaireIdAndOrdreAndActifTrue(
                etablissementId, anneeScolaireId, ordre)) {
            throw new ConflitException(CodeErreur.PERIODE_ACADEMIQUE_ORDRE_DUPLIQUE,
                    "Une période active de cette année scolaire porte déjà cet ordre.");
        }
    }

    /**
     * R4/R5 : discrimine sur le nom de la contrainte violée (même principe
     * que {@code MatiereService}/{@code AnneeScolaireService}). Le
     * non-chevauchement (R4) n'est jamais pré-vérifié en applicatif : seule
     * la contrainte d'exclusion fait foi, rattrapée ici en 409.
     */
    private RuntimeException traduireViolation(RuntimeException e) {
        String contrainte = UtilitairesAcademique.nomContrainteViolee(e);
        if (contrainte == null) {
            return e;
        }
        return switch (contrainte) {
            case "uk_periodes_academiques_libelle_actif" -> new ConflitException(
                    CodeErreur.PERIODE_ACADEMIQUE_LIBELLE_DUPLIQUE,
                    "Une période active de cette année scolaire porte déjà ce libellé.");
            case "uk_periodes_academiques_ordre_actif" -> new ConflitException(
                    CodeErreur.PERIODE_ACADEMIQUE_ORDRE_DUPLIQUE,
                    "Une période active de cette année scolaire porte déjà cet ordre.");
            case "ex_periodes_academiques_chevauchement" -> new ConflitException(
                    CodeErreur.PERIODE_ACADEMIQUE_PERIODE_CHEVAUCHANTE,
                    "Cette période chevauche une autre période active de la même année scolaire.");
            default -> e;
        };
    }
}
