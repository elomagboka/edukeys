package tg.novadigital.edukeys.eleve.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.ClasseInscriptionQuery;
import tg.novadigital.edukeys.academique.ClasseInscriptionQuery.ClassePourInscription;
import tg.novadigital.edukeys.academique.OffreAdmissionQuery;
import tg.novadigital.edukeys.admission.DossierAdmissionInscription;
import tg.novadigital.edukeys.admission.DossierAdmissionInscription.DossierPourInscription;
import tg.novadigital.edukeys.common.exception.CodeErreur;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.repository.ContrainteViolee;
import tg.novadigital.edukeys.common.securite.IdentifiantConnexion;
import tg.novadigital.edukeys.common.texte.NormalisationTexte;
import tg.novadigital.edukeys.eleve.domain.Eleve;
import tg.novadigital.edukeys.eleve.domain.Inscription;
import tg.novadigital.edukeys.eleve.repository.EleveRepository;
import tg.novadigital.edukeys.eleve.repository.InscriptionRepository;
import tg.novadigital.edukeys.etablissement.EtablissementCourantQuery;
import tg.novadigital.edukeys.etablissement.EtablissementCourantQuery.ParametresInscription;
import tg.novadigital.edukeys.identite.CreateurCompteEleve;
import tg.novadigital.edukeys.identite.EmetteurMotDePasseTemporaire;

/**
 * Transaction unique de l'inscription (US-08) : tout ou rien. Séparée de {@link InscriptionService} pour que
 * l'orchestrateur reste non transactionnel (création de la ligne de compteur AVANT cette transaction).
 *
 * <p>Ordre des verrous, à respecter partout (US-11 comprise) : <strong>dossier, puis classe, puis compteur
 * de matricule</strong>. Ordre des règles : voir {@link #executer}. Le matricule est généré APRÈS toutes les
 * vérifications de règle et AVANT les écritures : un refus n'a rien consommé, un échec ultérieur annule
 * l'incrément avec la transaction.</p>
 *
 * <p>Homonymes simultanés sur deux dossiers DISTINCTS : non garanti (aucun verrou ne les sérialise), risque
 * accepté et documenté — la détection est une aide à la saisie, pas une contrainte d'intégrité.</p>
 */
@Component
public class InscriptionTransactionnelle {

    static final String CONTRAINTE_ELEVE_DEMANDE = "uk_eleves_demande_admission";
    static final String CONTRAINTE_DEMANDE_ELEVE = "uk_demandes_admission_eleve";
    static final String CONTRAINTE_IDENTIFIANT = "uk_utilisateurs_identifiant_connexion_actif";

    private final DossierAdmissionInscription dossierAdmissionInscription;
    private final ClasseInscriptionQuery classeInscriptionQuery;
    private final EtablissementCourantQuery etablissementCourantQuery;
    private final OffreAdmissionQuery libellesQuery;
    private final EleveRepository eleveRepository;
    private final InscriptionRepository inscriptionRepository;
    private final GenerateurMatricule generateurMatricule;
    private final CreateurCompteEleve createurCompteEleve;
    private final EmetteurMotDePasseTemporaire emetteurMotDePasseTemporaire;
    private final PolitiqueExpirationMotDePasseEleve politiqueExpiration;
    private final Clock clock;
    private final EntityManager entityManager;

    public InscriptionTransactionnelle(
            DossierAdmissionInscription dossierAdmissionInscription,
            ClasseInscriptionQuery classeInscriptionQuery,
            EtablissementCourantQuery etablissementCourantQuery,
            OffreAdmissionQuery libellesQuery,
            EleveRepository eleveRepository,
            InscriptionRepository inscriptionRepository,
            GenerateurMatricule generateurMatricule,
            CreateurCompteEleve createurCompteEleve,
            EmetteurMotDePasseTemporaire emetteurMotDePasseTemporaire,
            PolitiqueExpirationMotDePasseEleve politiqueExpiration,
            Clock clock,
            EntityManager entityManager) {
        this.dossierAdmissionInscription = dossierAdmissionInscription;
        this.classeInscriptionQuery = classeInscriptionQuery;
        this.etablissementCourantQuery = etablissementCourantQuery;
        this.libellesQuery = libellesQuery;
        this.eleveRepository = eleveRepository;
        this.inscriptionRepository = inscriptionRepository;
        this.generateurMatricule = generateurMatricule;
        this.createurCompteEleve = createurCompteEleve;
        this.emetteurMotDePasseTemporaire = emetteurMotDePasseTemporaire;
        this.politiqueExpiration = politiqueExpiration;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /**
     * Règles, dans l'ordre :
     * <ol>
     *   <li>verrou du dossier (404 s'il est absent, inactif ou d'un autre établissement) ;</li>
     *   <li>déjà inscrit : 409 ; version périmée : 409 {@code ADMISSION_MODIFICATION_CONCURRENTE} ;</li>
     *   <li>dossier non accepté : 422 ;</li>
     *   <li>verrou de la classe (404) ; inactive : 422 ;</li>
     *   <li>année de la classe différente de celle du dossier : 422 ; année clôturée : 422 ;</li>
     *   <li>niveau de la classe différent de celui du dossier : 422 ;</li>
     *   <li>homonyme actif non confirmé : 409 ; classe pleine : 422 ;</li>
     *   <li>matricule (compteur sous verrou) ; compte ; élève et inscription ; mot de passe temporaire ; dossier marqué inscrit ;</li>
     *   <li>flush explicite : une violation d'unicité sous course devient un 409, jamais un 500.</li>
     * </ol>
     */
    @Transactional
    public ResultatInscription executer(CommandeInscription commande) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();

        DossierPourInscription dossier = dossierAdmissionInscription.verrouillerPourInscription(commande.demandeAdmissionId());
        // « Déjà inscrit » AVANT la version : inscrire le dossier incrémente sa version, donc la perdante d'une
        // course (même dossier, deux classes) arrive avec une version périmée ; la réponse exacte est « déjà inscrit ».
        if (dossier.eleveId() != null) {
            throw new ConflitException(CodeErreur.INSCRIPTION_DEJA_EFFECTUEE, "Ce dossier a déjà donné lieu à une inscription.");
        }
        if (commande.versionDemande() != dossier.version()) {
            throw new ConflitException(CodeErreur.ADMISSION_MODIFICATION_CONCURRENTE,
                    "Le dossier a été modifié entre-temps, veuillez le recharger.");
        }
        if (!dossier.acceptee()) {
            throw new RegleMetierViolee(CodeErreur.INSCRIPTION_DEMANDE_NON_ACCEPTEE,
                    "Seul un dossier accepté peut donner lieu à une inscription (statut actuel : %s).".formatted(dossier.statut()));
        }

        ClassePourInscription classe = classeInscriptionQuery.verrouillerPourInscription(commande.classeId());
        if (!classe.actif()) {
            throw new RegleMetierViolee(CodeErreur.INSCRIPTION_CLASSE_INACTIVE, "Cette classe est désactivée.");
        }
        if (!classe.anneeScolaireId().equals(dossier.anneeScolaireId())) {
            throw new RegleMetierViolee(CodeErreur.INSCRIPTION_ANNEE_INCOHERENTE,
                    "L'année de la classe n'est pas celle du dossier d'admission.");
        }
        if (!classe.anneeModifiable()) {
            throw new RegleMetierViolee(CodeErreur.INSCRIPTION_ANNEE_CLOTUREE, "L'année scolaire de cette classe est clôturée.");
        }
        if (!classe.niveauId().equals(dossier.niveauId())) {
            throw new RegleMetierViolee(CodeErreur.INSCRIPTION_NIVEAU_INCOHERENT,
                    "Le niveau de la classe n'est pas celui demandé dans le dossier d'admission.");
        }

        String nomNormalise = NormalisationTexte.normaliserPourComparaison(dossier.nom());
        String prenomsNormalises = NormalisationTexte.normaliserPourComparaison(dossier.prenoms());
        if (!commande.confirmerHomonyme()) {
            verifierAbsenceHomonyme(etablissementId, nomNormalise, prenomsNormalises, dossier);
        }

        if (classe.effectifMax() != null && inscriptionRepository.countByClasseIdAndActifTrue(classe.id()) >= classe.effectifMax()) {
            throw new RegleMetierViolee(CodeErreur.INSCRIPTION_CLASSE_COMPLETE,
                    "La classe %s a atteint son effectif maximal (%d).".formatted(classe.libelle(), classe.effectifMax()));
        }

        Instant maintenant = clock.instant();
        ParametresInscription etablissement = etablissementCourantQuery.parametresInscription();
        String matricule = generateurMatricule.generer(etablissementId, etablissement.code(), classe.anneeDateDebut().getYear());

        String nomComplet = dossier.nom().toUpperCase(Locale.ROOT) + " " + dossier.prenoms();
        UUID utilisateurId = createurCompteEleve.creerCompteEleve(matricule, nomComplet);

        Eleve eleve = eleveRepository.save(new Eleve(etablissementId, matricule, dossier.nom(), dossier.prenoms(),
                dossier.dateNaissance(), dossier.lieuNaissance(), dossier.sexe(), dossier.nationalite(),
                dossier.etablissementOrigine(), utilisateurId, dossier.id()));
        Inscription inscription = inscriptionRepository.save(new Inscription(etablissementId, eleve,
                classe.anneeScolaireId(), classe.id(), classe.siteId(), maintenant));

        Instant expiration = politiqueExpiration.calculer(maintenant, classe.anneeDateDebut(), ZoneId.of(etablissement.fuseauHoraire()));
        String motDePasseTemporaire = emetteurMotDePasseTemporaire.emettreAvecExpiration(utilisateurId, expiration);

        dossierAdmissionInscription.marquerInscrite(dossier.id(), eleve.getId(), maintenant);

        flusherEnTraduisantLesCourses();

        return new ResultatInscription(
                eleve.getId(), inscription.getId(), matricule, eleve.getNom(), eleve.getPrenoms(),
                new ResultatInscription.Reference(classe.id(), classe.libelle()),
                new ResultatInscription.Reference(classe.niveauId(), classe.niveauLibelle()),
                classe.filiereId() == null ? null : new ResultatInscription.Reference(classe.filiereId(), classe.filiereLibelle()),
                new ResultatInscription.Reference(classe.anneeScolaireId(), classe.anneeLibelle()),
                classe.siteId(), maintenant,
                new ResultatInscription.CompteEleve(IdentifiantConnexion.normaliser(matricule), motDePasseTemporaire, expiration));
    }

    private void verifierAbsenceHomonyme(UUID etablissementId, String nomNormalise, String prenomsNormalises, DossierPourInscription dossier) {
        List<EleveRepository.Homonyme> lignes =
                eleveRepository.rechercherHomonymes(etablissementId, nomNormalise, prenomsNormalises, dossier.dateNaissance());
        // Une entrée par élève : la requête renvoie une ligne par inscription active (homonyme inscrit sur deux
        // années), triées de la plus récente à la plus ancienne pour un même matricule.
        Map<String, EleveRepository.Homonyme> parEleve = new LinkedHashMap<>();
        lignes.forEach(h -> parEleve.putIfAbsent(h.matricule(), h));
        List<EleveRepository.Homonyme> homonymes = List.copyOf(parEleve.values());
        if (homonymes.isEmpty()) {
            return;
        }
        // Une seule requête de libellés pour toutes les classes (CLAUDE.md, règle 10).
        Set<UUID> classeIds = new HashSet<>();
        homonymes.forEach(h -> {
            if (h.classeId() != null) {
                classeIds.add(h.classeId());
            }
        });
        Map<UUID, String> libelles = libellesQuery.libelles(classeIds);
        List<Map<String, Object>> details = new ArrayList<>();
        for (EleveRepository.Homonyme h : homonymes) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("matricule", h.matricule());
            ligne.put("classe", h.classeId() == null ? null : libelles.get(h.classeId()));
            details.add(ligne);
        }
        throw new ConflitException(CodeErreur.ELEVE_HOMONYME,
                "Un élève actif de même nom, prénoms et date de naissance existe déjà ; confirmez pour inscrire quand même.",
                Map.of("homonymes", details));
    }

    /**
     * Flush explicite, DANS la transaction : une violation d'unicité provoquée par une course (double clic
     * parallèle, identifiant pris entre-temps) est traduite en 409 ici, jamais laissée remonter au commit.
     */
    private void flusherEnTraduisantLesCourses() {
        try {
            entityManager.flush();
        } catch (RuntimeException e) {
            String contrainte = ContrainteViolee.nom(e);
            if (CONTRAINTE_ELEVE_DEMANDE.equals(contrainte) || CONTRAINTE_DEMANDE_ELEVE.equals(contrainte)) {
                throw new ConflitException(CodeErreur.INSCRIPTION_DEJA_EFFECTUEE, "Ce dossier a déjà donné lieu à une inscription.");
            }
            if (CONTRAINTE_IDENTIFIANT.equals(contrainte)) {
                throw new ConflitException(CodeErreur.UTILISATEUR_IDENTIFIANT_DUPLIQUE, "Cet identifiant est déjà utilisé sur la plateforme.");
            }
            throw e;
        }
    }
}
