package tg.novadigital.edukeys.admission.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tg.novadigital.edukeys.admission.domain.CanalAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.TypePieceAdmission;
import tg.novadigital.edukeys.admission.repository.DemandeAdmissionRepository;
import tg.novadigital.edukeys.academique.OffreAdmissionQuery;
import tg.novadigital.edukeys.admission.AdmissionProperties;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.fichier.DetecteurTypeFichier;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.etablissement.EtablissementPublicQuery;

/**
 * Cœur métier de la pré-inscription en ligne (US-06). Turnstile (règle 4) et
 * le champ piège (règle 6) sont vérifiés ici, avant tout accès base autre que
 * la résolution de l'établissement — jamais de contournement silencieux.
 *
 * <p><b>Orchestrateur non transactionnel</b> (I5, revue) : {@link #creer}
 * n'ouvre lui-même aucune transaction. Il enchaîne les étapes {@code
 * @Transactional} de {@link InsertionDemandeAdmissionTransactionnelle}, chacune
 * dans sa propre transaction courte — voir la javadoc de cette classe pour la
 * raison précise (une violation de contrainte sous soumission concurrente ne
 * doit jamais laisser la connexion Postgres dans un état avorté au moment de
 * la relecture de récupération).</p>
 */
@Service
public class DemandeAdmissionService {

    private static final int AGE_MIN_ANNEES = 2;
    private static final int AGE_MAX_ANNEES = 30;
    private static final String REFERENCE_FICTIVE_HONEYPOT = "PRE-0000-000000";
    /** 3e revue, point 1 : même forme (26 caractères Crockford Base32) qu'un code de suivi réel — rien ne distingue le honeypot en aval. */
    private static final String CODE_SUIVI_FICTIF_HONEYPOT = "0".repeat(26);

    private final DemandeAdmissionRepository demandeAdmissionRepository;
    private final EtablissementPublicQuery etablissementPublicQuery;
    private final OffreAdmissionQuery offreAdmissionQuery;
    private final StockagePiecesJointes stockagePiecesJointes;
    private final AdmissionProperties proprietes;
    private final InsertionDemandeAdmissionTransactionnelle insertionTransactionnelle;

    public DemandeAdmissionService(
            DemandeAdmissionRepository demandeAdmissionRepository,
            EtablissementPublicQuery etablissementPublicQuery,
            OffreAdmissionQuery offreAdmissionQuery,
            StockagePiecesJointes stockagePiecesJointes,
            AdmissionProperties proprietes,
            InsertionDemandeAdmissionTransactionnelle insertionTransactionnelle) {
        this.demandeAdmissionRepository = demandeAdmissionRepository;
        this.etablissementPublicQuery = etablissementPublicQuery;
        this.offreAdmissionQuery = offreAdmissionQuery;
        this.stockagePiecesJointes = stockagePiecesJointes;
        this.proprietes = proprietes;
        this.insertionTransactionnelle = insertionTransactionnelle;
    }

    /**
     * Résultat d'une soumission publique (I4, revue) : {@code reference} reste
     * disponible pour l'usage interne (notification au dossier existant,
     * canal ADMIN) mais {@code codeSuivi} est la seule valeur que le
     * contrôleur public expose (3e revue, point 1) — {@code nouveau} pour le
     * seul usage interne du contrôleur (toujours 201 côté public, quel que
     * soit son état — plus de statut ni de date dans l'accusé public).
     */
    public record Accuse(String reference, String codeSuivi, boolean nouveau) {
    }

    /** Résultat interne de {@link #creer} : distingue création et dossier existant retrouvé (règle 3, idempotence). */
    private record ResultatCreation(DemandeAdmission demande, boolean nouveau) {
    }

    /**
     * Vérification Turnstile déplacée dans {@code FiltreVerificationTurnstileAdmission}
     * (B2+I1, revue) : exécutée avant même l'analyse multipart, hors de toute
     * transaction. Ce service n'a donc plus jamais à vérifier le jeton — il
     * n'atteint même pas ce code si le filtre a refusé.
     */
    public Accuse soumettrePublique(
            String codeEtablissement,
            CommandeDemandeAdmission donnees,
            String siteWebHoneypot,
            List<MultipartFile> pieces,
            List<String> typesPieces,
            String adresseIp) {

        // Règle 6 : honeypot rempli -> succès fictif, rien n'est enregistré.
        if (siteWebHoneypot != null && !siteWebHoneypot.isBlank()) {
            return new Accuse(REFERENCE_FICTIVE_HONEYPOT, CODE_SUIVI_FICTIF_HONEYPOT, true);
        }

        EtablissementPublicQuery.EtablissementPublic etablissement = etablissementPublicQuery.resoudreParCode(codeEtablissement)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_ETABLISSEMENT_INTROUVABLE, "Établissement introuvable."));
        if (!etablissement.admissionsOuvertes()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_FERMEE, "La pré-inscription en ligne n'est pas ouverte pour cet établissement.");
        }

        if (!donnees.consentementDonnees()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_CONSENTEMENT_MANQUANT,
                    "Le consentement au traitement des données est obligatoire pour une soumission en ligne.");
        }

        ResultatCreation resultat = creer(etablissement.id(), donnees, CanalAdmission.PUBLIC, pieces, typesPieces,
                Instant.now(), hacherIp(adresseIp));
        // I4 : même réponse publique, dossier nouveau ou existant — seul le code
        // de suivi et un message générique sortent, jamais le statut ni la date.
        // La référence séquentielle (3e revue, point 1) reste interne.
        return new Accuse(resultat.demande().getReference(), resultat.demande().getCodeSuivi(), resultat.nouveau());
    }

    public record OffrePublique(String etablissementNom, String etablissementLogoUrl, boolean admissionsOuvertes,
                                 OffreAdmissionQuery.OffreAdmission offre) {
    }

    @Transactional(readOnly = true)
    public OffrePublique obtenirOffrePublique(String codeEtablissement) {
        EtablissementPublicQuery.EtablissementPublic etablissement = etablissementPublicQuery.resoudreParCode(codeEtablissement)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_ETABLISSEMENT_INTROUVABLE, "Établissement introuvable."));
        OffreAdmissionQuery.OffreAdmission offre = etablissement.admissionsOuvertes()
                ? offreAdmissionQuery.offreOuverte(etablissement.id())
                : new OffreAdmissionQuery.OffreAdmission(null, null, List.of());
        return new OffrePublique(etablissement.nom(), etablissement.logoUrl(), etablissement.admissionsOuvertes(), offre);
    }

    /** Résultat de {@link #creerAdmin} : distingue création et dossier existant retrouvé (règle 3, idempotence). */
    public record ResultatAdmin(DemandeAdmission demande, boolean nouveau) {
    }

    public ResultatAdmin creerAdmin(CommandeDemandeAdmission donnees, List<MultipartFile> pieces, List<String> typesPieces) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        ResultatCreation resultat = creer(etablissementId, donnees, CanalAdmission.ADMIN, pieces, typesPieces, Instant.now(), null);
        return new ResultatAdmin(resultat.demande(), resultat.nouveau());
    }

    /**
     * Cœur commun aux deux canaux (règle 3 : l'idempotence vaut aussi pour
     * ADMIN). Orchestrateur non transactionnel (I5, revue) : chaque étape qui
     * touche la base vit dans {@link InsertionDemandeAdmissionTransactionnelle},
     * dans sa propre transaction courte.
     */
    private ResultatCreation creer(
            UUID etablissementId,
            CommandeDemandeAdmission donnees,
            CanalAdmission canal,
            List<MultipartFile> pieces,
            List<String> typesPieces,
            Instant maintenant,
            String ipHash) {

        String nom = normaliser(donnees.nom());
        String prenoms = normaliser(donnees.prenoms());
        String responsableNom = normaliser(donnees.responsableNom());
        String responsablePrenoms = normaliser(donnees.responsablePrenoms());
        String nomNormalise = DemandeAdmission.normaliserPourComparaison(nom);
        String prenomsNormalises = DemandeAdmission.normaliserPourComparaison(prenoms);
        validerAge(donnees.dateNaissance());
        // Pièces validées AVANT la recherche de doublon (I4, 2e revue US-06) : sinon un
        // envoi sans pièce répondait 201 si l'enfant avait déjà un dossier et 422
        // (acte de naissance manquant) sinon — un tiers connaissant nom, prénoms et
        // date de naissance pouvait tester si un enfant a postulé dans une école.
        // Toute erreur de validation doit donc tomber de la même façon, dossier
        // existant ou non.
        List<PiecePreparee> piecesPreparees = validerEtPreparerPieces(pieces, typesPieces);

        // Étape 1 (lecture seule, transaction courte) : offre + vérification du cas séquentiel.
        InsertionDemandeAdmissionTransactionnelle.OffreEtDoublon phase1 = insertionTransactionnelle.resoudreOffreEtVerifierDoublon(
                etablissementId, donnees.niveauId(), donnees.classeId(), nomNormalise, prenomsNormalises, donnees.dateNaissance());
        if (phase1.doublon() != null) {
            return new ResultatCreation(phase1.doublon(), false);
        }

        try {
            // Étape 2 (écriture, transaction courte) : insertion effective.
            DemandeAdmission sauvee = insererAvecRattrapageDuCompteur(
                    etablissementId, nom, prenoms, responsableNom, responsablePrenoms, phase1.anneeScolaireId(),
                    canal, maintenant, ipHash, piecesPreparees, donnees);
            return new ResultatCreation(sauvee, true);
        } catch (RuntimeException e) {
            if (!InsertionDemandeAdmissionTransactionnelle.estViolationContrainteDoublon(e)) {
                throw e;
            }
            // I5 : soumission strictement concurrente, les deux ayant passé l'étape 1. La
            // transaction de l'étape 2 est déjà terminée (rollback) à ce stade — jamais la
            // même connexion Postgres avortée pour cette étape 3, une nouvelle transaction.
            DemandeAdmission existante = insertionTransactionnelle.relireApresConflit(
                    etablissementId, phase1.anneeScolaireId(), nomNormalise, prenomsNormalises, donnees.dateNaissance());
            return new ResultatCreation(existante, false);
        }
    }

    /**
     * Insère la demande, et rejoue **une seule fois** si l'échec vient de la
     * création concurrente de la ligne de compteur de référence (3e revue,
     * point 2) : {@code SELECT ... FOR UPDATE} ne verrouille pas une ligne
     * inexistante, donc les deux premières soumissions d'un établissement — ou
     * les deux premières après un passage d'année — tentent chacune de la
     * créer, et la perdante violait {@code uk_compteurs_reference_admission_annee},
     * remontée en 500 au parent.
     *
     * <p>Le rattrapage ne peut pas se faire à l'intérieur de la transaction
     * fautive : PostgreSQL l'annule dès la violation, toute requête suivante y
     * serait refusée. Il se fait donc ici, hors transaction : le nouvel appel
     * ouvre une transaction neuve, où la ligne committée par la gagnante est
     * visible. Une seule tentative supplémentaire suffit — la ligne ne peut
     * plus manquer ensuite — et cela ne coûte rien au régime courant : elle
     * n'est créée qu'une fois par établissement et par année.</p>
     */
    private DemandeAdmission insererAvecRattrapageDuCompteur(
            UUID etablissementId, String nom, String prenoms, String responsableNom, String responsablePrenoms,
            UUID anneeScolaireId, CanalAdmission canal, Instant maintenant, String ipHash,
            List<PiecePreparee> piecesPreparees, CommandeDemandeAdmission donnees) {
        try {
            return insertionTransactionnelle.inserer(etablissementId, nom, prenoms, responsableNom, responsablePrenoms,
                    anneeScolaireId, canal, maintenant, ipHash, piecesPreparees, donnees);
        } catch (RuntimeException e) {
            if (!InsertionDemandeAdmissionTransactionnelle.estViolationContrainteCompteur(e)) {
                throw e;
            }
            return insertionTransactionnelle.inserer(etablissementId, nom, prenoms, responsableNom, responsablePrenoms,
                    anneeScolaireId, canal, maintenant, ipHash, piecesPreparees, donnees);
        }
    }

    @Transactional(readOnly = true)
    public Page<DemandeAdmissionResumeVue> lister(tg.novadigital.edukeys.admission.domain.StatutAdmission statut, Pageable pageable) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        Page<DemandeAdmission> page = statut == null
                ? demandeAdmissionRepository.findByEtablissementIdAndActifTrueOrderByDateSoumissionDesc(etablissementId, pageable)
                : demandeAdmissionRepository.findByEtablissementIdAndActifTrueAndStatutOrderByDateSoumissionDesc(etablissementId, statut, pageable);

        // Anti N+1 (CLAUDE.md, règle 10) : une seule résolution de libellés pour toute la page.
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        page.getContent().forEach(d -> {
            ids.add(d.getNiveauId());
            if (d.getClasseId() != null) {
                ids.add(d.getClasseId());
            }
        });
        java.util.Map<UUID, String> libelles = offreAdmissionQuery.libelles(ids);

        return page.map(d -> new DemandeAdmissionResumeVue(
                d.getId(), d.getReference(), d.getNom(), d.getPrenoms(),
                libelles.get(d.getNiveauId()), d.getClasseId() == null ? null : libelles.get(d.getClasseId()),
                d.getStatut().name(), d.getCanal().name(), d.getDateSoumission()));
    }

    public record DemandeAdmissionResumeVue(
            UUID id, String reference, String nom, String prenoms,
            String niveauLibelle, String classeLibelle, String statut, String canal, Instant dateSoumission) {
    }

    @Transactional(readOnly = true)
    public DemandeAdmission obtenir(UUID id) {
        DemandeAdmission demande = demandeAdmissionRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_INTROUVABLE, "Demande d'admission introuvable."));
        // Mineur (revue) : un dossier désactivé logiquement ne doit jamais être exposé.
        if (!demande.isActif()) {
            throw new RessourceIntrouvableException(CodeErreur.ADMISSION_INTROUVABLE, "Demande d'admission introuvable.");
        }
        return demande;
    }

    public record DemandeEtPieces(DemandeAdmission demande, List<PieceJointeAdmission> pieces) {
    }

    /** Un seul {@link #obtenir(UUID)}, jamais deux (mineur, revue) : détail du dossier + ses pièces en un aller-retour logique. */
    @Transactional(readOnly = true)
    public DemandeEtPieces obtenirAvecPieces(UUID id) {
        DemandeAdmission demande = obtenir(id);
        return new DemandeEtPieces(demande, stockagePiecesJointes.lister(id));
    }

    @Transactional(readOnly = true)
    public List<PieceJointeAdmission> listerPieces(UUID demandeId) {
        obtenir(demandeId);
        return stockagePiecesJointes.lister(demandeId);
    }

    @Transactional
    public PieceJointeAdmission ajouterPiece(UUID demandeId, String typePieceBrut, MultipartFile fichier) {
        DemandeAdmission demande = obtenir(demandeId);
        if (!demande.estModifiable()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_MODIFICATION_REFUSEE_HORS_ATTENTE,
                    "Les pièces d'un dossier ne sont modifiables que tant qu'il est en attente.");
        }
        long dejaPresentes = stockagePiecesJointes.compterActives(demandeId);
        if (dejaPresentes >= proprietes.getNombreMaxPieces()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_TROP_DE_PIECES, "Nombre maximal de pièces atteint pour ce dossier.");
        }
        TypePieceAdmission type = analyserType(typePieceBrut);
        PiecePreparee piece = validerFichier(fichier, type);
        // 3e revue, point 5 : même fichier joint deux fois -> refus explicite
        // (422), jamais la violation de l'index unique non rattrapée (500). Le
        // parent croit avoir joint deux pièces distinctes, un écartement
        // silencieux le laisserait dans l'erreur.
        if (stockagePiecesJointes.existeDejaPourDemande(demandeId, piece.empreinteSha256())) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_PIECE_DUPLIQUEE, "Cette pièce est déjà jointe à ce dossier.");
        }
        return stockagePiecesJointes.enregistrer(
                demande.getEtablissementId(), demandeId, piece.type(), piece.nomOriginal(), piece.typeMime(), piece.contenu(), piece.empreinteSha256());
    }

    @Transactional
    public void desactiverPiece(UUID demandeId, UUID pieceId) {
        DemandeAdmission demande = obtenir(demandeId);
        if (!demande.estModifiable()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_MODIFICATION_REFUSEE_HORS_ATTENTE,
                    "Les pièces d'un dossier ne sont modifiables que tant qu'il est en attente.");
        }
        PieceJointeAdmission piece = stockagePiecesJointes.trouver(demandeId, pieceId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_PIECE_INTROUVABLE, "Pièce introuvable."));
        stockagePiecesJointes.desactiver(piece);
    }

    public record PieceEtContenu(PieceJointeAdmission piece, byte[] contenu) {
    }

    /** B3 (revue) : seul appelant de {@link StockagePiecesJointes#lireContenu}, le contenu n'est donc lu qu'ici. */
    @Transactional(readOnly = true)
    public PieceEtContenu telechargerPiece(UUID demandeId, UUID pieceId) {
        obtenir(demandeId);
        PieceJointeAdmission piece = stockagePiecesJointes.trouver(demandeId, pieceId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_PIECE_INTROUVABLE, "Pièce introuvable."));
        byte[] contenu = stockagePiecesJointes.lireContenu(pieceId)
                .orElseThrow(() -> new RessourceIntrouvableException(CodeErreur.ADMISSION_PIECE_INTROUVABLE, "Pièce introuvable."));
        return new PieceEtContenu(piece, contenu);
    }

    // ------------------------------------------------------------------
    // Règles internes
    // ------------------------------------------------------------------

    private void validerAge(LocalDate dateNaissance) {
        if (dateNaissance == null || !dateNaissance.isBefore(LocalDate.now())) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_AGE_INVALIDE, "La date de naissance doit être passée.");
        }
        int age = Period.between(dateNaissance, LocalDate.now()).getYears();
        if (age < AGE_MIN_ANNEES || age > AGE_MAX_ANNEES) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_AGE_INVALIDE,
                    "L'âge doit être compris entre %d et %d ans.".formatted(AGE_MIN_ANNEES, AGE_MAX_ANNEES));
        }
    }

    private static String normaliser(String valeur) {
        return valeur == null ? null : valeur.trim();
    }

    /**
     * I7 (revue) : HMAC-SHA256 avec clé secrète de configuration, jamais un
     * simple SHA-256 salé — un sel connu (valeur par défaut historique
     * {@code "changez-moi"}) permettait de reconstituer un dictionnaire
     * d'adresses IP plausibles et de les comparer aux empreintes stockées.
     * L'échec de démarrage hors {@code local}/{@code test} quand la clé est
     * absente ou vaut la valeur par défaut est porté par
     * {@link tg.novadigital.edukeys.admission.VerificateurCleHachageIpAdmission}.
     * Toujours à sens unique : aucun moyen de retrouver l'IP en clair.
     */
    private String hacherIp(String adresseIp) {
        if (adresseIp == null || adresseIp.isBlank()) {
            return null;
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    proprietes.getSelHachageIp().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] empreinte = mac.doFinal(adresseIp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(empreinte);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 indisponible dans cette JVM.", e);
        }
    }

    /** Package-visible : réutilisé par {@link InsertionDemandeAdmissionTransactionnelle} (I5). */
    record PiecePreparee(TypePieceAdmission type, String nomOriginal, String typeMime, byte[] contenu, String empreinteSha256) {
    }

    private List<PiecePreparee> validerEtPreparerPieces(List<MultipartFile> fichiers, List<String> typesPieces) {
        List<MultipartFile> pieces = fichiers == null ? List.of() : fichiers;
        List<String> types = typesPieces == null ? List.of() : typesPieces;
        if (pieces.size() != types.size()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_PIECE_TYPE_MANQUANT, "Chaque pièce doit porter un type.");
        }
        if (pieces.size() > proprietes.getNombreMaxPieces()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_TROP_DE_PIECES,
                    "Au plus %d pièces sont acceptées par demande.".formatted(proprietes.getNombreMaxPieces()));
        }

        List<PiecePreparee> preparees = new ArrayList<>();
        java.util.Set<String> empreintesVues = new java.util.HashSet<>();
        long tailleTotale = 0;
        boolean acteNaissancePresent = false;
        for (int i = 0; i < pieces.size(); i++) {
            TypePieceAdmission type = analyserType(types.get(i));
            if (type == TypePieceAdmission.ACTE_NAISSANCE) {
                acteNaissancePresent = true;
            }
            PiecePreparee piece = validerFichier(pieces.get(i), type);
            // 3e revue, point 5 : même fichier envoyé deux fois dans un même
            // dossier (geste banal sur mobile) -> refus explicite (422), jamais
            // la violation de l'index unique non rattrapée (500).
            if (!empreintesVues.add(piece.empreinteSha256())) {
                throw new RegleMetierViolee(CodeErreur.ADMISSION_PIECE_DUPLIQUEE, "Cette pièce est déjà jointe à ce dossier.");
            }
            tailleTotale += piece.contenu().length;
            preparees.add(piece);
        }
        if (tailleTotale > proprietes.getTailleMaxTotalPieces().toBytes()) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_TAILLE_TOTALE_PIECES_DEPASSEE,
                    "La taille cumulée des pièces dépasse la limite autorisée.");
        }
        if (!acteNaissancePresent) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_ACTE_NAISSANCE_MANQUANT, "Une copie de l'acte de naissance est obligatoire.");
        }
        return preparees;
    }

    private TypePieceAdmission analyserType(String valeur) {
        try {
            return TypePieceAdmission.valueOf(valeur);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_PIECE_TYPE_MANQUANT, "Type de pièce invalide ou manquant.");
        }
    }

    private PiecePreparee validerFichier(MultipartFile fichier, TypePieceAdmission type) {
        byte[] contenu = lireContenu(fichier);
        if (contenu.length == 0) {
            throw new tg.novadigital.edukeys.common.exception.RegleMetierViolee(CodeErreur.ADMISSION_PIECE_VIDE, "Fichier vide.");
        }
        if (contenu.length > proprietes.getTailleMaxPiece().toBytes()) {
            throw new tg.novadigital.edukeys.common.exception.FichierTropVolumineuxException(
                    CodeErreur.ADMISSION_PIECE_TROP_VOLUMINEUSE, "Pièce trop volumineuse.");
        }
        String typeMime = DetecteurTypeFichier.detecterTypeMimePieceAdmission(contenu)
                .orElseThrow(() -> new tg.novadigital.edukeys.common.exception.FormatFichierNonSupporteException(
                        CodeErreur.ADMISSION_PIECE_FORMAT_NON_SUPPORTE, "Format non pris en charge : seuls PDF, JPEG et PNG sont acceptés."));
        if (typeMime.equals("application/pdf") && contientContenuSuspect(contenu)) {
            throw new tg.novadigital.edukeys.common.exception.FormatFichierNonSupporteException(
                    CodeErreur.ADMISSION_PIECE_CONTENU_SUSPECT, "PDF au contenu actif non autorisé.");
        }
        String empreinte = calculerEmpreinteSha256(contenu);
        String nomOriginal = nettoyerNomFichier(fichier.getOriginalFilename());
        return new PiecePreparee(type, nomOriginal, typeMime, contenu, empreinte);
    }

    private static boolean contientContenuSuspect(byte[] contenu) {
        String texte = new String(contenu, StandardCharsets.ISO_8859_1);
        return texte.contains("/JavaScript") || texte.contains("/JS") || texte.contains("/Launch");
    }

    private static byte[] lireContenu(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (IOException e) {
            throw new RegleMetierViolee(CodeErreur.ADMISSION_PIECE_VIDE, "Fichier illisible.");
        }
    }

    private static String nettoyerNomFichier(String nom) {
        if (nom == null || nom.isBlank()) {
            return "piece";
        }
        String sansChemin = nom.replace("\\", "/");
        sansChemin = sansChemin.substring(sansChemin.lastIndexOf('/') + 1);
        String nettoye = sansChemin.replaceAll("[\\p{Cntrl}]", "").trim();
        // Mineur (revue) : la colonne nom_original est VARCHAR(255).
        return nettoye.length() > 255 ? nettoye.substring(0, 255) : nettoye;
    }

    private static String calculerEmpreinteSha256(byte[] contenu) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible dans cette JVM.", e);
        }
    }
}
