package tg.novadigital.edukeys.identite.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
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

    public UtilisateurService(
            UtilisateurRepository utilisateurRepository,
            JetonRafraichissementRepository jetonRafraichissementRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.jetonRafraichissementRepository = jetonRafraichissementRepository;
    }

    public Utilisateur obtenir(UUID utilisateurId) {
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
     * liste globale.
     */
    public Page<Utilisateur> listerTous(Pageable pageable) {
        return utilisateurRepository.findAll(pageable);
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
