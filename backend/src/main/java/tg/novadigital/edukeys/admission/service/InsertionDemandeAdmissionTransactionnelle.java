package tg.novadigital.edukeys.admission.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.OffreAdmissionQuery;
import tg.novadigital.edukeys.admission.domain.CanalAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.admission.repository.DemandeAdmissionRepository;
import tg.novadigital.edukeys.admission.service.DemandeAdmissionService.PiecePreparee;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Les trois seules étapes de la création d'un dossier d'admission qui
 * touchent la base — chacune dans sa propre transaction courte (I5, revue).
 *
 * <p>Séparée de {@link DemandeAdmissionService} précisément pour que
 * l'orchestrateur ({@code DemandeAdmissionService#creer}) reste
 * <b>non transactionnel</b> : appeler ces méthodes {@code @Transactional}
 * depuis un bean distinct passe par le proxy Spring (l'auto-invocation depuis
 * la même classe l'aurait contourné), et surtout garantit qu'une violation de
 * contrainte dans {@link #inserer} ferme complètement sa transaction avant
 * que {@link #relireApresConflit} n'en ouvre une nouvelle — jamais la même
 * connexion Postgres avortée. Le nom exact de la contrainte est vérifié : une
 * autre violation d'intégrité ne doit jamais être avalée silencieusement.</p>
 */
@Component
public class InsertionDemandeAdmissionTransactionnelle {

    /** Nom de l'index unique partiel posé en V13 — seule violation récupérable ici. */
    static final String CONTRAINTE_DOUBLON = "uk_demandes_admission_doublon";

    /** Unicité de la ligne de compteur de référence (une par établissement et par année). */
    static final String CONTRAINTE_COMPTEUR = "uk_compteurs_reference_admission_annee";

    private final DemandeAdmissionRepository demandeAdmissionRepository;
    private final OffreAdmissionQuery offreAdmissionQuery;
    private final GenerateurReferenceAdmission generateurReferenceAdmission;
    private final GenerateurCodeSuiviAdmission generateurCodeSuiviAdmission;
    private final StockagePiecesJointes stockagePiecesJointes;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public InsertionDemandeAdmissionTransactionnelle(
            DemandeAdmissionRepository demandeAdmissionRepository,
            OffreAdmissionQuery offreAdmissionQuery,
            GenerateurReferenceAdmission generateurReferenceAdmission,
            GenerateurCodeSuiviAdmission generateurCodeSuiviAdmission,
            StockagePiecesJointes stockagePiecesJointes,
            ApplicationEventPublisher eventPublisher,
            EntityManager entityManager) {
        this.demandeAdmissionRepository = demandeAdmissionRepository;
        this.offreAdmissionQuery = offreAdmissionQuery;
        this.generateurReferenceAdmission = generateurReferenceAdmission;
        this.generateurCodeSuiviAdmission = generateurCodeSuiviAdmission;
        this.stockagePiecesJointes = stockagePiecesJointes;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    /** Résultat de la résolution de l'offre + vérification préalable de doublon (chemin séquentiel courant). */
    public record OffreEtDoublon(UUID anneeScolaireId, DemandeAdmission doublon) {
    }

    /**
     * Première étape, lecture seule : résout l'offre ouverte et vérifie s'il
     * existe déjà un doublon actif. Si oui, publie l'accusé (I4) directement
     * ici — encore dans une transaction active, condition nécessaire pour
     * qu'un {@code @TransactionalEventListener(AFTER_COMMIT)} se déclenche
     * (voir {@link NotificationAccuseReceptionAdmissionListener}).
     */
    @Transactional
    public OffreEtDoublon resoudreOffreEtVerifierDoublon(
            UUID etablissementId, UUID niveauId, UUID classeId, String nomNormalise, String prenomsNormalises, LocalDate dateNaissance) {
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            OffreAdmissionQuery.OffreAdmission offre = offreAdmissionQuery.offreOuverte(etablissementId);
            if (offre.anneeScolaireId() == null) {
                throw new RegleMetierViolee(CodeErreur.ADMISSION_FERMEE, "Aucune année scolaire ouverte à l'admission.");
            }
            if (!offreAdmissionQuery.verifierChoix(etablissementId, offre.anneeScolaireId(), niveauId, classeId)) {
                throw new RegleMetierViolee(CodeErreur.ADMISSION_CHOIX_NIVEAU_CLASSE_INVALIDE,
                        "Le niveau et/ou la classe choisis ne sont pas valides pour cette année.");
            }
            List<DemandeAdmission> doublons = demandeAdmissionRepository.rechercherDoublonsActifs(
                    etablissementId, offre.anneeScolaireId(), nomNormalise, prenomsNormalises, dateNaissance);
            if (doublons.isEmpty()) {
                return new OffreEtDoublon(offre.anneeScolaireId(), null);
            }
            DemandeAdmission existante = doublons.get(0);
            publierAccuse(existante);
            return new OffreEtDoublon(offre.anneeScolaireId(), existante);
        }
    }

    /**
     * Deuxième étape, écriture : insère le dossier et ses pièces. Une
     * violation de {@value #CONTRAINTE_DOUBLON} (course entre deux
     * soumissions concurrentes passées toutes deux la vérification
     * précédente) est laissée remonter telle quelle — jamais rattrapée ici,
     * c'est à l'appelant ({@code DemandeAdmissionService#creer}, hors de
     * toute transaction) de la reconnaître et de déclencher {@link
     * #relireApresConflit}.
     */
    @Transactional
    public DemandeAdmission inserer(
            UUID etablissementId,
            String nom,
            String prenoms,
            String responsableNom,
            String responsablePrenoms,
            UUID anneeScolaireId,
            CanalAdmission canal,
            Instant maintenant,
            String ipHash,
            List<PiecePreparee> piecesPreparees,
            CommandeDemandeAdmission donnees) {

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            String reference = genererReferenceUnique(etablissementId);
            String codeSuivi = generateurCodeSuiviAdmission.genererCodeSuivi();
            String email = donnees.responsableEmail() == null ? null : donnees.responsableEmail().trim().toLowerCase(Locale.ROOT);
            Instant consentement = canal == CanalAdmission.PUBLIC ? maintenant : null;

            DemandeAdmission demande = new DemandeAdmission(
                    etablissementId,
                    reference,
                    codeSuivi,
                    anneeScolaireId,
                    donnees.niveauId(),
                    donnees.classeId(),
                    nom,
                    prenoms,
                    donnees.dateNaissance(),
                    donnees.lieuNaissance(),
                    donnees.sexe(),
                    donnees.nationalite(),
                    donnees.etablissementOrigine(),
                    responsableNom,
                    responsablePrenoms,
                    donnees.responsableLien(),
                    donnees.responsableTelephone().trim(),
                    email,
                    canal,
                    maintenant,
                    consentement,
                    ipHash);

            // I5 : le filet de sécurité posé en base (V13, uk_demandes_admission_doublon)
            // reste la seule garantie fiable sous soumission strictement concurrente —
            // la vérification applicative de l'étape précédente couvre le cas
            // séquentiel. Une violation ici remonte telle quelle (voir javadoc de méthode).
            DemandeAdmission sauvee = demandeAdmissionRepository.save(demande);
            entityManager.flush();

            for (PiecePreparee piece : piecesPreparees) {
                stockagePiecesJointes.enregistrer(
                        etablissementId, sauvee.getId(), piece.type(), piece.nomOriginal(), piece.typeMime(), piece.contenu(), piece.empreinteSha256());
            }
            entityManager.flush(); // Règle 12 CLAUDE.md : flush avant fermeture de la portée établissement.

            eventPublisher.publishEvent(new DemandeAdmissionSoumiseEvent(
                    sauvee.getId(), sauvee.getReference(), sauvee.getResponsableTelephone(), sauvee.getResponsableEmail()));
            return sauvee;
        }
    }

    /**
     * Troisième étape, appelée uniquement sur violation de {@value
     * #CONTRAINTE_DOUBLON} : la transaction de {@link #inserer} est déjà
     * terminée (rollback) au moment où cette méthode démarre — jamais la
     * même connexion Postgres avortée. Relit le dossier gagnant de la course
     * et publie son accusé (I4), avec le même traitement que le chemin
     * idempotent (téléphone du dossier EXISTANT, plafond I2).
     */
    @Transactional
    public DemandeAdmission relireApresConflit(
            UUID etablissementId, UUID anneeScolaireId, String nomNormalise, String prenomsNormalises, LocalDate dateNaissance) {
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            List<DemandeAdmission> doublons = demandeAdmissionRepository.rechercherDoublonsActifs(
                    etablissementId, anneeScolaireId, nomNormalise, prenomsNormalises, dateNaissance);
            entityManager.flush(); // Règle 12 CLAUDE.md.
            if (doublons.isEmpty()) {
                // Improbable (le gagnant de la course a changé de statut entre la violation et cette relecture) : jamais silencieux.
                throw new RessourceIntrouvableException(CodeErreur.ADMISSION_REFERENCE_CONFLIT,
                        "Conflit détecté mais dossier introuvable à la relecture, réessayez.");
            }
            DemandeAdmission existante = doublons.get(0);
            publierAccuse(existante);
            return existante;
        }
    }

    /** I4 : accusé envoyé au dossier existant (jamais aux coordonnées resaisies), soumis au plafond I2 côté {@code Notificateur}. */
    private void publierAccuse(DemandeAdmission existante) {
        // US-08, Q-D : un dossier ACCEPTEE (éventuellement déjà inscrit) ne reçoit plus d'accusé de
        // réception — il serait trompeur de réannoncer « bien enregistrée » à un parent dont l'enfant est
        // accepté. La réponse publique, elle, reste strictement identique (aucune fuite du statut).
        if (existante.getStatut() == StatutAdmission.ACCEPTEE) {
            return;
        }
        eventPublisher.publishEvent(new DemandeAdmissionSoumiseEvent(
                existante.getId(), existante.getReference(), existante.getResponsableTelephone(), existante.getResponsableEmail()));
    }

    private String genererReferenceUnique(UUID etablissementId) {
        String reference = generateurReferenceAdmission.genererReference(etablissementId);
        if (demandeAdmissionRepository.existsByEtablissementIdAndReferenceAndActifTrue(etablissementId, reference)) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_REFERENCE_CONFLIT, "Conflit sur la génération de référence, réessayez.");
        }
        return reference;
    }

    /**
     * Vrai si l'exception provient précisément de {@value #CONTRAINTE_DOUBLON}
     * — toute autre violation d'intégrité remonte inchangée. Accepte {@link
     * RuntimeException} au sens large (pas seulement {@link
     * DataIntegrityViolationException}) : le flush explicite de {@link
     * #inserer} (règle 12 CLAUDE.md) passe par l'{@code EntityManager} brut,
     * pas par un repository Spring Data — la traduction d'exception
     * ({@code PersistenceExceptionTranslationPostProcessor}) ne s'applique
     * donc pas, et Hibernate remonte un {@code jakarta.persistence.PersistenceException}
     * brut, jamais un {@code DataIntegrityViolationException} Spring.
     */
    public static boolean estViolationContrainteDoublon(RuntimeException e) {
        return CONTRAINTE_DOUBLON.equals(nomContrainteViolee(e));
    }

    /**
     * Vrai si l'exception vient de la création concurrente de la ligne de
     * compteur (3e revue, point 2) : les deux premières soumissions d'un
     * établissement, ou les deux premières après un passage d'année. La
     * perdante n'a rien à relire — il suffit de rejouer l'insertion dans une
     * transaction neuve, où la ligne créée par la gagnante est visible.
     */
    public static boolean estViolationContrainteCompteur(RuntimeException e) {
        return CONTRAINTE_COMPTEUR.equals(nomContrainteViolee(e));
    }

    private static String nomContrainteViolee(RuntimeException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
