package tg.novadigital.edukeys.etablissement.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.initialisation.ChargeurReferentielType;
import tg.novadigital.edukeys.common.initialisation.InitialisateurReferentiel;
import tg.novadigital.edukeys.common.initialisation.ReferentielType;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.etablissement.domain.Site;
import tg.novadigital.edukeys.etablissement.domain.LogoEtablissement;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.LogoEtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.SiteRepository;
import tg.novadigital.edukeys.etablissement.web.CreerEtablissementRequestDto;
import tg.novadigital.edukeys.etablissement.web.ModifierEtablissementRequestDto;

import static tg.novadigital.edukeys.etablissement.service.UtilitairesEtablissement.normaliserCode;

/**
 * Cycle de vie d'un établissement (US-00). Le point délicat de ce service est
 * {@link #creer} : création de l'établissement, de son site principal et
 * initialisation du référentiel pédagogique dans une <b>seule et même
 * transaction</b> (arbitrage explicite de la spec T-10) — un échec à
 * n'importe quelle étape fait tout rollback, aucun établissement à moitié
 * initialisé n'est possible. Le SPI {@link InitialisateurReferentiel} n'a
 * donc pas besoin d'être idempotent ni rejouable : une seule transaction
 * suffit et évite la complexité d'un mécanisme de reprise.
 */
@Service
public class EtablissementService {

    private final EtablissementRepository etablissementRepository;
    private final SiteRepository siteRepository;
    private final LogoEtablissementRepository logoEtablissementRepository;
    private final ChargeurReferentielType chargeurReferentielType;
    private final List<InitialisateurReferentiel> initialisateursReferentiel;
    private final EntityManager entityManager;

    public EtablissementService(
            EtablissementRepository etablissementRepository,
            SiteRepository siteRepository,
            LogoEtablissementRepository logoEtablissementRepository,
            ChargeurReferentielType chargeurReferentielType,
            List<InitialisateurReferentiel> initialisateursReferentiel,
            EntityManager entityManager) {
        this.etablissementRepository = etablissementRepository;
        this.siteRepository = siteRepository;
        this.logoEtablissementRepository = logoEtablissementRepository;
        this.chargeurReferentielType = chargeurReferentielType;
        this.initialisateursReferentiel = initialisateursReferentiel;
        this.entityManager = entityManager;
    }

    /**
     * Transaction unique de bout en bout (voir la Javadoc de classe) :
     * <ol>
     *   <li>valider + persister {@link Etablissement} ({@code referentielInitialise = false}) ;</li>
     *   <li>{@code flush()} — la ligne doit exister avant les FK des étapes suivantes ;</li>
     *   <li>ouvrir explicitement un contexte d'établissement (piège central de
     *       cette US : sans lui, la persistance du site échoue —
     *       {@link tg.novadigital.edukeys.common.domain.RemplisseurEtablissement}
     *       exige un contexte ouvert, R4.2) ;</li>
     *   <li>créer le site principal ;</li>
     *   <li>initialiser le référentiel pédagogique (liste vide acceptée tant
     *       qu'aucun module ne fournit d'implémentation concrète, T-10) ;</li>
     *   <li>marquer le référentiel initialisé (R7).</li>
     * </ol>
     */
    @Transactional
    public Etablissement creer(CreerEtablissementRequestDto requete) {
        String code = normaliserCode(requete.code());
        String email = requete.email().toLowerCase(Locale.ROOT);

        if (etablissementRepository.existsByCodeIgnoreCaseAndActifTrue(code)) {
            throw new ConflitException("Un établissement actif porte déjà ce code.");
        }
        if (etablissementRepository.existsByEmailIgnoreCaseAndActifTrue(email)) {
            throw new ConflitException("Un établissement actif porte déjà cet email.");
        }

        Etablissement etablissement = new Etablissement(code, requete.nom(), requete.typeEtablissement(), requete.ville(), email);
        etablissement.modifierIdentite(requete.nom(), requete.sigle(), requete.typeEtablissement());
        etablissement.modifierCoordonnees(
                requete.ville(), requete.quartier(), requete.boitePostale(), requete.adresseLigne(),
                email, requete.telephone(), requete.siteWeb(),
                etablissement.getFuseauHoraire(), etablissement.getDeviseCode(), etablissement.getLangueDefaut());

        etablissement = etablissementRepository.save(etablissement);
        entityManager.flush();

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissement.getId())) {
            Site sitePrincipal = new Site(
                    etablissement.getId(),
                    code + "-PRINCIPAL",
                    etablissement.getNom(),
                    true,
                    etablissement.getVille(),
                    etablissement.getQuartier(),
                    etablissement.getAdresseLigne(),
                    etablissement.getTelephone());
            siteRepository.save(sitePrincipal);
            etablissement.incrementerSitesActifs();

            UUID etablissementId = etablissement.getId();
            ReferentielType modele = chargeurReferentielType.charger();
            initialisateursReferentiel.forEach(initialisateur -> initialisateur.initialiser(etablissementId, modele));

            etablissement.marquerReferentielInitialise();
            etablissement = etablissementRepository.save(etablissement);

            // Flush explicite avant que le bloc ne referme le contexte : Hibernate
            // diffère l'INSERT/UPDATE réel (et donc le déclenchement de
            // RemplisseurEtablissement) jusqu'au prochain flush — sans lui, ce
            // flush n'arriverait qu'au commit de la transaction @Transactional,
            // hors de ce try-with-resources déjà refermé, et
            // ContexteEtablissementAbsentException serait levée en conditions
            // réelles (piège T-10, A1, distinct de l'ouverture de contexte
            // elle-même).
            entityManager.flush();
        }

        return etablissement;
    }

    public Etablissement obtenir(UUID id) {
        return etablissementRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Établissement introuvable."));
    }

    /** Existence brute, sans lever d'exception : évite au controller un try/catch utilisé comme contrôle de flux. */
    public boolean existe(UUID id) {
        return etablissementRepository.existsById(id);
    }

    /**
     * Réservée à SUPER_ADMIN (opération plateforme, {@code ETABLISSEMENT_CREER}) :
     * {@code Etablissement} échappe au filtre multi-établissement, donc cette
     * méthode retourne réellement toutes les lignes de la plateforme. Le
     * point d'entrée ADMIN est {@link #obtenirCourant()}.
     */
    public Page<Etablissement> lister(Pageable pageable) {
        return etablissementRepository.findAll(pageable);
    }

    /**
     * Établissement de l'appelant courant (ADMIN), résolu depuis le contexte
     * multi-établissement ouvert par son JWT — jamais depuis un identifiant
     * de requête, pour ne jamais dépendre d'un {@code GardeAccesEtablissement}
     * qui pourrait être oublié.
     */
    public Etablissement obtenirCourant() {
        return obtenir(ContexteEtablissement.exigerEtablissementId());
    }

    @Transactional
    public Etablissement modifier(UUID id, ModifierEtablissementRequestDto requete) {
        Etablissement etablissement = obtenir(id);
        String email = requete.email().toLowerCase(Locale.ROOT);

        if (!email.equalsIgnoreCase(etablissement.getEmail())
                && etablissementRepository.existsByEmailIgnoreCaseAndActifTrueAndIdNot(email, id)) {
            throw new ConflitException("Un établissement actif porte déjà cet email.");
        }

        etablissement.modifierIdentite(requete.nom(), requete.sigle(), requete.typeEtablissement());
        etablissement.modifierCoordonnees(
                requete.ville(), requete.quartier(), requete.boitePostale(), requete.adresseLigne(),
                email, requete.telephone(), requete.siteWeb(),
                requete.fuseauHoraire(), requete.deviseCode(), requete.langueDefaut());

        return etablissementRepository.save(etablissement);
    }

    /** R9 : cascade logique, jamais un DELETE SQL — sites et logo de l'établissement sont désactivés avec lui. */
    @Transactional
    public void desactiver(UUID id) {
        Etablissement etablissement = obtenir(id);
        etablissement.desactiver();
        etablissementRepository.save(etablissement);

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(id)) {
            siteRepository.findByEtablissementIdAndActifTrueOrderByNomAsc(id).forEach(site -> {
                site.desactiver();
                siteRepository.save(site);
                etablissement.decrementerSitesActifs();
            });
            etablissementRepository.save(etablissement);
            logoEtablissementRepository.findByEtablissementIdAndActifTrue(id).ifPresent(logo -> {
                logo.desactiver();
                logoEtablissementRepository.save(logo);
            });
            entityManager.flush(); // même raison que dans creer(...) : flush avant fermeture du contexte.
        }
    }

    /**
     * R10 : refuse si le code ou l'email est désormais porté par un autre
     * établissement actif.
     *
     * <p>R4 (« tout établissement actif a exactement un site principal
     * actif ») doit rester vraie après réactivation : {@link #desactiver}
     * désactive en cascade les sites (et le logo) actifs de l'établissement,
     * donc symétriquement cette méthode réactive tous les sites actuellement
     * inactifs de l'établissement, ainsi que son logo inactif — approximation
     * volontairement simple (durcissement post-revue T-10) plutôt qu'un
     * mécanisme de mémorisation de « ce qui a été désactivé par cette
     * cascade précisément » : un site déjà inactif avant la désactivation de
     * l'établissement (désactivé par un gestionnaire pour une tout autre
     * raison) est donc réactivé lui aussi. Comportement simple et prévisible,
     * documenté ici. S'assure ensuite qu'un site principal actif existe
     * (sinon le premier site réactivé, par ordre alphabétique, est promu) —
     * sans quoi aucun {@code POST .../principal} ne serait possible après un
     * aller-retour désactivation/réactivation.</p>
     */
    @Transactional
    public void reactiver(UUID id) {
        Etablissement etablissement = obtenir(id);

        if (etablissementRepository.existsByCodeIgnoreCaseAndActifTrueAndIdNot(etablissement.getCode(), id)) {
            throw new ConflitException("Le code de cet établissement est désormais porté par un autre établissement actif.");
        }
        if (etablissementRepository.existsByEmailIgnoreCaseAndActifTrueAndIdNot(etablissement.getEmail(), id)) {
            throw new ConflitException("L'email de cet établissement est désormais porté par un autre établissement actif.");
        }

        etablissement.reactiver();
        etablissementRepository.save(etablissement);

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(id)) {
            List<Site> sitesReactives = siteRepository.findByEtablissementIdAndActifFalseOrderByNomAsc(id);
            sitesReactives.forEach(site -> {
                site.reactiver();
                siteRepository.save(site);
                etablissement.incrementerSitesActifs();
            });

            boolean aucunPrincipalActif = siteRepository.findByEtablissementIdAndPrincipalTrueAndActifTrue(id).isEmpty();
            if (aucunPrincipalActif && !sitesReactives.isEmpty()) {
                Site nouveauPrincipal = sitesReactives.get(0);
                nouveauPrincipal.designerPrincipal();
                siteRepository.save(nouveauPrincipal);
            }

            etablissementRepository.save(etablissement);
            logoEtablissementRepository.findByEtablissementIdAndActifFalse(id).ifPresent(logo -> {
                logo.reactiver();
                logoEtablissementRepository.save(logo);
            });
            entityManager.flush(); // même raison que dans creer(...)/desactiver(...) : flush avant fermeture du contexte.
        }
    }
}
