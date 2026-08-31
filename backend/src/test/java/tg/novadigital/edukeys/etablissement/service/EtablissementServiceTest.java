package tg.novadigital.edukeys.etablissement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.initialisation.ChargeurReferentielType;
import tg.novadigital.edukeys.common.initialisation.InitialisateurReferentiel;
import tg.novadigital.edukeys.common.initialisation.ReferentielType;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.etablissement.domain.Site;
import tg.novadigital.edukeys.etablissement.domain.TypeEtablissement;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.LogoEtablissementRepository;
import tg.novadigital.edukeys.etablissement.repository.SiteRepository;
import tg.novadigital.edukeys.etablissement.web.CreerEtablissementRequestDto;
import tg.novadigital.edukeys.etablissement.web.ModifierEtablissementRequestDto;

/**
 * Tests unitaires du service de référence T-10 (US-00). Le point critique
 * vérifié ci-dessous est {@link EtablissementService#creer} : la création de
 * l'établissement, de son site principal et l'appel à chaque
 * {@link InitialisateurReferentiel} doivent se produire dans un contexte
 * multi-établissement effectivement ouvert (piège central signalé par la
 * spec T-10) — sans quoi la persistance du site échouerait en conditions
 * réelles (voir {@code RemplisseurEtablissement}, R4.2).
 */
class EtablissementServiceTest {

    private EtablissementRepository etablissementRepository;
    private SiteRepository siteRepository;
    private LogoEtablissementRepository logoEtablissementRepository;
    private ChargeurReferentielType chargeurReferentielType;
    private EntityManager entityManager;
    private EtablissementService service;

    @BeforeEach
    void configurer() {
        etablissementRepository = mock(EtablissementRepository.class);
        siteRepository = mock(SiteRepository.class);
        logoEtablissementRepository = mock(LogoEtablissementRepository.class);
        chargeurReferentielType = mock(ChargeurReferentielType.class);
        entityManager = mock(EntityManager.class);

        when(etablissementRepository.save(any(Etablissement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(siteRepository.save(any(Site.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeurReferentielType.charger())
                .thenReturn(new ReferentielType(List.of(), List.of(), List.of(), List.of()));
    }

    private EtablissementService nouveauService(List<InitialisateurReferentiel> initialisateurs) {
        return new EtablissementService(
                etablissementRepository, siteRepository, logoEtablissementRepository,
                chargeurReferentielType, initialisateurs, entityManager);
    }

    private static CreerEtablissementRequestDto requeteCreation() {
        return new CreerEtablissementRequestDto(
                "csj", "Complexe Scolaire Jean", "CSJ", TypeEtablissement.COMPLEXE,
                "Lomé", "Bè", null, null, "Contact@CSJ.TG", null, null);
    }

    @Test
    void creeUnSitePrincipalEtAppelleLesInitialisateurs_dansLaMemeTransaction() {
        service = nouveauService(List.of());
        when(etablissementRepository.existsByCodeIgnoreCaseAndActifTrue("CSJ")).thenReturn(false);
        when(etablissementRepository.existsByEmailIgnoreCaseAndActifTrue("contact@csj.tg")).thenReturn(false);

        Etablissement etablissement = service.creer(requeteCreation());

        assertThat(etablissement.getCode()).isEqualTo("CSJ");
        assertThat(etablissement.getEmail()).isEqualTo("contact@csj.tg");
        assertThat(etablissement.isReferentielInitialise()).isTrue();
        assertThat(etablissement.getNombreSitesActifs()).isEqualTo(1);

        org.mockito.ArgumentCaptor<Site> captor = org.mockito.ArgumentCaptor.forClass(Site.class);
        verify(siteRepository).save(captor.capture());
        Site sitePrincipal = captor.getValue();
        assertThat(sitePrincipal.isPrincipal()).isTrue();
        assertThat(sitePrincipal.getCode()).isEqualTo("CSJ-PRINCIPAL");
    }

    @Test
    void appelleChaqueInitialisateurReferentiel_avecLIdentifiantDeLetablissementCree() {
        InitialisateurReferentiel initialisateur = mock(InitialisateurReferentiel.class);
        service = nouveauService(List.of(initialisateur));
        when(etablissementRepository.existsByCodeIgnoreCaseAndActifTrue(any())).thenReturn(false);
        when(etablissementRepository.existsByEmailIgnoreCaseAndActifTrue(any())).thenReturn(false);

        Etablissement etablissement = service.creer(requeteCreation());

        verify(initialisateur).initialiser(org.mockito.ArgumentMatchers.eq(etablissement.getId()), any());
    }

    @Test
    void refuseLaCreation_quandLeCodeEstDejaPorteParUnEtablissementActif() {
        service = nouveauService(List.of());
        when(etablissementRepository.existsByCodeIgnoreCaseAndActifTrue("CSJ")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(requeteCreation())).isInstanceOf(ConflitException.class);
        verify(siteRepository, never()).save(any());
    }

    @Test
    void refuseLaCreation_quandLEmailEstDejaPorteParUnEtablissementActif() {
        service = nouveauService(List.of());
        when(etablissementRepository.existsByCodeIgnoreCaseAndActifTrue(any())).thenReturn(false);
        when(etablissementRepository.existsByEmailIgnoreCaseAndActifTrue("contact@csj.tg")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(requeteCreation())).isInstanceOf(ConflitException.class);
    }

    @Test
    void leveUneExceptionRessourceIntrouvable_quandEtablissementInexistant() {
        service = nouveauService(List.of());
        UUID id = UUID.randomUUID();
        when(etablissementRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(id)).isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void modifierRefuseLeConflitEmail_quandUnAutreEtablissementActifLePorteDeja() {
        service = nouveauService(List.of());
        UUID id = UUID.randomUUID();
        Etablissement etablissement = new Etablissement("CSJ", "Complexe", TypeEtablissement.COMPLEXE, "Lomé", "ancien@csj.tg");
        when(etablissementRepository.findById(id)).thenReturn(Optional.of(etablissement));
        when(etablissementRepository.existsByEmailIgnoreCaseAndActifTrueAndIdNot("nouveau@csj.tg", id)).thenReturn(true);

        ModifierEtablissementRequestDto requete = new ModifierEtablissementRequestDto(
                "Complexe", "CSJ", TypeEtablissement.COMPLEXE, "Lomé", null, null, null,
                "nouveau@csj.tg", null, null, "Africa/Lome", "XOF", "fr");

        assertThatThrownBy(() -> service.modifier(id, requete)).isInstanceOf(ConflitException.class);
    }

    @Test
    void desactiverEstCascadeLogique_surLesSitesEtLeLogo() {
        service = nouveauService(List.of());
        UUID id = UUID.randomUUID();
        Etablissement etablissement = new Etablissement("CSJ", "Complexe", TypeEtablissement.COMPLEXE, "Lomé", "csj@csj.tg");
        etablissement.incrementerSitesActifs();
        when(etablissementRepository.findById(id)).thenReturn(Optional.of(etablissement));
        Site site = new Site(id, "CSJ-PRINCIPAL", "Complexe", true, "Lomé", null, null, null);
        when(siteRepository.findByEtablissementIdAndActifTrueOrderByNomAsc(id)).thenReturn(List.of(site));
        when(siteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(logoEtablissementRepository.findByEtablissementIdAndActifTrue(id)).thenReturn(Optional.empty());

        service.desactiver(id);

        assertThat(etablissement.isActif()).isFalse();
        assertThat(site.isActif()).isFalse();
        assertThat(etablissement.getNombreSitesActifs()).isZero();
        verify(etablissementRepository, org.mockito.Mockito.atLeastOnce()).save(etablissement);
    }

    @Test
    void reactiverRefuse_quandLeCodeEstDesormaisPorteParUnAutreEtablissementActif() {
        service = nouveauService(List.of());
        UUID id = UUID.randomUUID();
        Etablissement etablissement = new Etablissement("CSJ", "Complexe", TypeEtablissement.COMPLEXE, "Lomé", "csj@csj.tg");
        etablissement.desactiver();
        when(etablissementRepository.findById(id)).thenReturn(Optional.of(etablissement));
        when(etablissementRepository.existsByCodeIgnoreCaseAndActifTrueAndIdNot("CSJ", id)).thenReturn(true);

        assertThatThrownBy(() -> service.reactiver(id)).isInstanceOf(ConflitException.class);
        assertThat(etablissement.isActif()).isFalse();
    }
}
