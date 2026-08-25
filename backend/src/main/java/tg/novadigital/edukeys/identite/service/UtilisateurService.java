package tg.novadigital.edukeys.identite.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.AffectationEtablissementRepository;
import tg.novadigital.edukeys.identite.repository.JetonRafraichissementRepository;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Gestion du cycle de vie du compte utilisateur. La désactivation d'un compte
 * révoque immédiatement tous ses refresh tokens actifs, effet de bord exigé
 * par l'arbitrage T-04 n°3 : un compte désactivé ne doit pas pouvoir se
 * reconnecter via un jeton émis avant sa désactivation.
 */
@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final JetonRafraichissementRepository jetonRafraichissementRepository;
    private final AffectationEtablissementRepository affectationEtablissementRepository;

    public UtilisateurService(
            UtilisateurRepository utilisateurRepository,
            JetonRafraichissementRepository jetonRafraichissementRepository,
            AffectationEtablissementRepository affectationEtablissementRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.jetonRafraichissementRepository = jetonRafraichissementRepository;
        this.affectationEtablissementRepository = affectationEtablissementRepository;
    }

    /**
     * <b>Non cadré par établissement — usage restreint à l'auto-lecture.</b>
     * {@link Utilisateur} n'étend pas {@code EntiteEtablissement} (ADR-0002) :
     * aucun filtre Hibernate ne borne cette lecture, et cette méthode ne
     * vérifie elle-même aucune appartenance. Elle n'est légitime que quand
     * l'identifiant recherché est celui de l'appelant authentifié lui-même
     * (voir {@code UtilisateurController#moi}, seul appelant à ce jour) —
     * jamais avec un identifiant fourni par un tiers ou lu dans un chemin
     * d'URL. Pour un accès administratif à un compte précis, borné à
     * l'établissement courant, voir {@link #obtenirDansEtablissementCourant(UUID)}.
     */
    public Utilisateur obtenir(UUID utilisateurId) {
        return utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    /**
     * Compte d'un établissement précis, réservé aux appelants portant
     * {@code UTILISATEUR_GERER} (ADMIN, cadré par son propre établissement,
     * ADR-0002 §5) : un ADMIN de l'établissement A ne doit jamais pouvoir
     * atteindre un compte dont la seule affectation est sur B, y compris par
     * accès direct à l'identifiant (T-05, sous-tâche 13,
     * {@code IsolationUtilisateursTest}). {@code Utilisateur} n'étant pas
     * filtré (ADR-0002), le cloisonnement est vérifié ici, explicitement, via
     * {@link AffectationEtablissementRepository#existsByUtilisateurIdAndEtablissementIdAndActifTrue}
     * — jamais déduit d'un simple {@code findById}.
     *
     * <p>Message d'erreur identique à un identifiant réellement inexistant :
     * un ADMIN de A ne doit pas pouvoir distinguer « ce compte n'existe pas »
     * de « ce compte existe, mais pas chez vous » (l'un fuiterait déjà
     * l'existence d'un compte dans un autre établissement).</p>
     */
    public Utilisateur obtenirDansEtablissementCourant(UUID utilisateurId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        boolean affecteAEtablissementCourant = affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId);
        if (!affecteAEtablissementCourant) {
            throw new RessourceIntrouvableException("Utilisateur introuvable.");
        }
        return utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    /**
     * Comptes de tous les établissements, sans distinction : {@link Utilisateur}
     * n'est délibérément pas rattaché à un établissement (voir sa javadoc et
     * ADR-0002) — un même compte peut porter plusieurs {@code AffectationEtablissement}
     * sur des établissements différents, donc « son » établissement n'existe
     * pas. Cet endpoint est réservé à SUPER_ADMIN (permission
     * {@code UTILISATEUR_GERER_PLATEFORME}), jamais à ADMIN : ADMIN porte
     * {@code UTILISATEUR_GERER} (comptes de son propre établissement), une
     * permission distincte précisément pour éviter qu'il n'obtienne cette
     * liste globale — voir {@link #listerParEtablissementCourant(Pageable)}
     * pour l'équivalent cadré.
     */
    public Page<Utilisateur> listerTous(Pageable pageable) {
        return utilisateurRepository.findAll(pageable);
    }

    /**
     * Comptes actifs de l'établissement courant (contexte ouvert par
     * {@code ContexteEtablissementFilter} depuis le JWT, ADR-0002), via une
     * affectation elle-même active. Pendant de {@link #listerTous(Pageable)}
     * pour {@code UTILISATEUR_GERER} (ADMIN) — jamais {@code UTILISATEUR_GERER_PLATEFORME}
     * seul ne doit atteindre la liste globale (T-05, sous-tâche 13).
     */
    public Page<Utilisateur> listerParEtablissementCourant(Pageable pageable) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return utilisateurRepository.findParEtablissementCourantActif(etablissementId, pageable);
    }

    @Transactional
    public void desactiverCompte(UUID utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        utilisateur.desactiver();
        utilisateurRepository.save(utilisateur);

        List<JetonRafraichissement> jetonsActifs =
                jetonRafraichissementRepository.findByUtilisateurIdAndActifTrue(utilisateurId);
        jetonsActifs.forEach(JetonRafraichissement::desactiver);
        jetonsActifs.forEach(jetonRafraichissementRepository::save);
    }
}
