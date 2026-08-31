package tg.novadigital.edukeys.etablissement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.etablissement.domain.Site;
import tg.novadigital.edukeys.etablissement.domain.TypeEtablissement;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.SiteRepository;
import tg.novadigital.edukeys.etablissement.web.CreerSiteRequestDto;
import tg.novadigital.edukeys.etablissement.web.ModifierSiteRequestDto;

/**
 * Tests unitaires du durcissement post-revue T-10 : (1) {@code creer}
 * incrémente le compteur dénormalisé {@code Etablissement#nombreSitesActifs},
 * {@code desactiver} le décrémente ; (2) {@code modifier} et
 * {@code designerPrincipal} vérifient désormais l'existence de
 * l'établissement au même titre que {@code lister}/{@code creer} — asymétrie
 * corrigée (IMPORTANT 7).
 */
class SiteServiceTest {

    private SiteRepository siteRepository;
    private EtablissementRepository etablissementRepository;
    private EntityManager entityManager;
    private SiteService service;

    @BeforeEach
    void configurer() {
        siteRepository = mock(SiteRepository.class);
        etablissementRepository = mock(EtablissementRepository.class);
        entityManager = mock(EntityManager.class);
        service = new SiteService(siteRepository, etablissementRepository, entityManager);

        when(siteRepository.save(any(Site.class))).thenAnswer(inv -> inv.getArgument(0));
        when(etablissementRepository.save(any(Etablissement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void creerIncrementeLeCompteurDeSitesActifsDeLEtablissement() {
        UUID etablissementId = UUID.randomUUID();
        Etablissement etablissement = new Etablissement("CSJ", "Complexe", TypeEtablissement.COMPLEXE, "Lomé", "csj@csj.tg");
        when(etablissementRepository.findById(etablissementId)).thenReturn(Optional.of(etablissement));
        when(siteRepository.findByEtablissementIdAndCodeIgnoreCaseAndActifTrue(any(), any())).thenReturn(Optional.empty());

        service.creer(etablissementId, new CreerSiteRequestDto("annexe", "Annexe", "Lomé", null, null, null));

        assertThat(etablissement.getNombreSitesActifs()).isEqualTo(1);
    }

    @Test
    void desactiverDecrementeLeCompteurDeSitesActifsDeLEtablissement() {
        UUID etablissementId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Etablissement etablissement = new Etablissement("CSJ", "Complexe", TypeEtablissement.COMPLEXE, "Lomé", "csj@csj.tg");
        etablissement.incrementerSitesActifs();
        etablissement.incrementerSitesActifs();
        when(etablissementRepository.findById(etablissementId)).thenReturn(Optional.of(etablissement));
        Site site = new Site(etablissementId, "ANNEXE", "Annexe", false, "Lomé", null, null, null);
        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));

        service.desactiver(etablissementId, siteId);

        assertThat(site.isActif()).isFalse();
        assertThat(etablissement.getNombreSitesActifs()).isEqualTo(1);
    }

    @Test
    void modifierRefuseEtablissementIntrouvable_avantDeChercherLeSite() {
        UUID etablissementId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        when(etablissementRepository.existsById(etablissementId)).thenReturn(false);

        assertThatThrownBy(() -> service.modifier(etablissementId, siteId,
                new ModifierSiteRequestDto("Nom", "Lomé", null, null, null)))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Établissement introuvable");
    }

    @Test
    void designerPrincipalRefuseEtablissementIntrouvable_avantDeChercherLeSite() {
        UUID etablissementId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        when(etablissementRepository.existsById(etablissementId)).thenReturn(false);

        assertThatThrownBy(() -> service.designerPrincipal(etablissementId, siteId))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Établissement introuvable");
    }
}
