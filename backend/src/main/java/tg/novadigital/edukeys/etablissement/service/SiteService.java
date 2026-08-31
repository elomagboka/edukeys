package tg.novadigital.edukeys.etablissement.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.etablissement.domain.Site;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.SiteRepository;
import tg.novadigital.edukeys.etablissement.web.CreerSiteRequestDto;
import tg.novadigital.edukeys.etablissement.web.ModifierSiteRequestDto;

import static tg.novadigital.edukeys.etablissement.service.UtilitairesEtablissement.normaliserCode;
import static tg.novadigital.edukeys.etablissement.service.UtilitairesEtablissement.verifierEtablissementExiste;

/**
 * Gestion des sites (annexes) d'un établissement (US-00,
 * docs/adr/0005-sites-et-annexes.md). Chaque méthode ouvre explicitement un
 * contexte multi-établissement sur l'établissement ciblé : {@code Site} est
 * une {@code EntiteEtablissement} filtrée par Hibernate, et l'appelant
 * (ADMIN via son propre contexte JWT, ou SUPER_ADMIN sans contexte ouvert)
 * ne peut pas être tenu pour garant que le filtre laisse déjà passer les
 * lignes de l'établissement demandé — le contrôle d'accès lui-même
 * ({@code GardeAccesEtablissement}, R11) a lieu en amont, dans le contrôleur.
 *
 * <p>Toute méthode publique prenant un {@code etablissementId} vérifie
 * l'existence de l'établissement en premier (durcissement post-revue) : sans
 * cela, {@code modifier}/{@code designerPrincipal}/{@code desactiver}
 * renvoyaient « Site introuvable » pour un établissement inexistant, au lieu
 * du message attendu « Établissement introuvable ».</p>
 */
@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final EtablissementRepository etablissementRepository;
    private final EntityManager entityManager;

    public SiteService(SiteRepository siteRepository, EtablissementRepository etablissementRepository, EntityManager entityManager) {
        this.siteRepository = siteRepository;
        this.etablissementRepository = etablissementRepository;
        this.entityManager = entityManager;
    }

    public List<Site> lister(UUID etablissementId) {
        verifierEtablissementExiste(etablissementRepository, etablissementId);
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            return siteRepository.findByEtablissementIdAndActifTrueOrderByNomAsc(etablissementId);
        }
    }

    @Transactional
    public Site creer(UUID etablissementId, CreerSiteRequestDto requete) {
        Etablissement etablissement = obtenirEtablissement(etablissementId);
        String code = normaliserCode(requete.code());

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            if (siteRepository.findByEtablissementIdAndCodeIgnoreCaseAndActifTrue(etablissementId, code).isPresent()) {
                throw new ConflitException("Un site actif porte déjà ce code dans cet établissement.");
            }
            Site site = new Site(etablissementId, code, requete.nom(), false,
                    requete.ville(), requete.quartier(), requete.adresseLigne(), requete.telephone());
            Site sauve = siteRepository.save(site);
            etablissement.incrementerSitesActifs();
            etablissementRepository.save(etablissement);
            entityManager.flush(); // flush avant fermeture du contexte (même piège T-10 A1 que EtablissementService.creer).
            return sauve;
        }
    }

    @Transactional
    public Site modifier(UUID etablissementId, UUID siteId, ModifierSiteRequestDto requete) {
        verifierEtablissementExiste(etablissementRepository, etablissementId);
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Site site = obtenirSiteActif(siteId);
            site.modifier(requete.nom(), requete.ville(), requete.quartier(), requete.adresseLigne(), requete.telephone());
            Site sauve = siteRepository.save(site);
            entityManager.flush();
            return sauve;
        }
    }

    /** Bascule transactionnelle : ancien principal actif -> false, nouveau -> true (R4). */
    @Transactional
    public void designerPrincipal(UUID etablissementId, UUID siteId) {
        verifierEtablissementExiste(etablissementRepository, etablissementId);
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Site nouveauPrincipal = obtenirSiteActif(siteId);

            siteRepository.findByEtablissementIdAndPrincipalTrueAndActifTrue(etablissementId)
                    .filter(ancien -> !ancien.getId().equals(siteId))
                    .ifPresent(ancien -> {
                        ancien.retirerPrincipal();
                        siteRepository.save(ancien);
                        // Flush immédiat : l'index unique partiel non déférable
                        // uk_sites_principal_actif interdit temporairement deux
                        // principaux actifs. Sans ce flush, Hibernate est libre
                        // d'ordonner les deux UPDATE comme il l'entend au
                        // prochain flush et peut activer le nouveau avant de
                        // désactiver l'ancien, violant la contrainte.
                        entityManager.flush();
                    });

            nouveauPrincipal.designerPrincipal();
            siteRepository.save(nouveauPrincipal);
            entityManager.flush();
        }
    }

    /** R5 : le site principal ne peut pas être désactivé directement — désigner un autre principal d'abord. */
    @Transactional
    public void desactiver(UUID etablissementId, UUID siteId) {
        Etablissement etablissement = obtenirEtablissement(etablissementId);
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            Site site = obtenirSiteActif(siteId);
            if (site.isPrincipal()) {
                throw new RegleMetierViolee(
                        "Le site principal ne peut pas être désactivé directement : désignez un autre site principal d'abord.");
            }
            site.desactiver();
            siteRepository.save(site);
            etablissement.decrementerSitesActifs();
            etablissementRepository.save(etablissement);
            entityManager.flush();
        }
    }

    private Site obtenirSiteActif(UUID siteId) {
        return siteRepository.findById(siteId)
                .filter(Site::isActif)
                .orElseThrow(() -> new RessourceIntrouvableException("Site introuvable."));
    }

    private Etablissement obtenirEtablissement(UUID etablissementId) {
        return etablissementRepository.findById(etablissementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Établissement introuvable."));
    }
}
