package tg.novadigital.edukeys.identite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissementAbsentException;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.AffectationEtablissementRepository;
import tg.novadigital.edukeys.identite.repository.JetonRafraichissementRepository;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;
import tg.novadigital.edukeys.identite.security.UtilisateurPrincipal;

class UtilisateurServiceTest {

    private UtilisateurRepository utilisateurRepository;
    private JetonRafraichissementRepository jetonRafraichissementRepository;
    private AffectationEtablissementRepository affectationEtablissementRepository;
    private UtilisateurService utilisateurService;

    @BeforeEach
    void configurer() {
        utilisateurRepository = mock(UtilisateurRepository.class);
        jetonRafraichissementRepository = mock(JetonRafraichissementRepository.class);
        affectationEtablissementRepository = mock(AffectationEtablissementRepository.class);
        utilisateurService = new UtilisateurService(
                utilisateurRepository, jetonRafraichissementRepository, affectationEtablissementRepository);
    }


    @Test
    void leveUneExceptionRessourceIntrouvable_quandUtilisateurInexistant() {
        UUID id = UUID.randomUUID();
        when(utilisateurRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> utilisateurService.desactiverCompte(id))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void desactiveLeCompteEtRevoqueSesJetonsActifs_quandDesactivationDemandee() {
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        UUID id = UUID.randomUUID();
        when(utilisateurRepository.findById(id)).thenReturn(Optional.of(utilisateur));

        JetonRafraichissement jeton1 = new JetonRafraichissement(utilisateur, "h1", Instant.now().plusSeconds(3600));
        JetonRafraichissement jeton2 = new JetonRafraichissement(utilisateur, "h2", Instant.now().plusSeconds(3600));
        when(jetonRafraichissementRepository.findByUtilisateurIdAndActifTrue(id)).thenReturn(List.of(jeton1, jeton2));

        utilisateurService.desactiverCompte(id);

        assertThat(utilisateur.isActif()).isFalse();
        assertThat(jeton1.isActif()).isFalse();
        assertThat(jeton2.isActif()).isFalse();
        verify(jetonRafraichissementRepository).save(jeton1);
        verify(jetonRafraichissementRepository).save(jeton2);
        verify(utilisateurRepository).save(utilisateur);
    }

    // ------------------------------------------------------------------
    // obtenirSoiMeme — revue post-T-05 : la signature ne prend plus un UUID
    // arbitraire mais le principal lui-même, rendant impossible de demander
    // un autre compte que le sien.
    // ------------------------------------------------------------------

    @Test
    void obtenirSoiMeme_renvoieLeCompteDuPrincipal() {
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        UtilisateurPrincipal principal = new UtilisateurPrincipal(utilisateurId, UUID.randomUUID(), Set.of("ADMIN"));
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));

        assertThat(utilisateurService.obtenirSoiMeme(principal)).isSameAs(utilisateur);
    }

    @Test
    void obtenirSoiMeme_leveUneExceptionRessourceIntrouvable_quandLeCompteDuPrincipalNexistePlus() {
        UtilisateurPrincipal principal =
                new UtilisateurPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of("ADMIN"));
        when(utilisateurRepository.findById(principal.utilisateurId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> utilisateurService.obtenirSoiMeme(principal))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // ------------------------------------------------------------------
    // obtenirDansEtablissementCourant — T-05, sous-tâche 13
    // ------------------------------------------------------------------

    @Test
    void obtenirDansEtablissementCourant_renvoieLeCompte_quandAffecteAEtablissementCourant() {
        UUID etablissementId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId))
                .thenReturn(true);
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));

        try (var portee = ContexteEtablissement.ouvrir(etablissementId)) {
            assertThat(utilisateurService.obtenirDansEtablissementCourant(utilisateurId)).isSameAs(utilisateur);
        }
    }

    @Test
    void obtenirDansEtablissementCourant_refuse_quandLeCompteAppartientAUnAutreEtablissement() {
        UUID etablissementCourant = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementCourant))
                .thenReturn(false);

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            assertThatThrownBy(() -> utilisateurService.obtenirDansEtablissementCourant(utilisateurId))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Test
    void obtenirDansEtablissementCourant_refuse_quandAucunContexteOuvert() {
        UUID utilisateurId = UUID.randomUUID();

        assertThatThrownBy(() -> utilisateurService.obtenirDansEtablissementCourant(utilisateurId))
                .isInstanceOf(ContexteEtablissementAbsentException.class);
    }

    // ------------------------------------------------------------------
    // listerParEtablissementCourant — T-05, sous-tâche 13
    // ------------------------------------------------------------------

    @Test
    void listerParEtablissementCourant_delegueAuRepositoryAvecLEtablissementDuContexte() {
        UUID etablissementId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Utilisateur utilisateur = new Utilisateur("paul@edukeys.tg", "hash", "Paul Martin", false);
        Page<Utilisateur> page = new PageImpl<>(List.of(utilisateur));
        when(utilisateurRepository.findParEtablissementCourantActif(etablissementId, pageable)).thenReturn(page);

        try (var portee = ContexteEtablissement.ouvrir(etablissementId)) {
            assertThat(utilisateurService.listerParEtablissementCourant(pageable)).containsExactly(utilisateur);
        }
    }

    @Test
    void listerParEtablissementCourant_refuse_quandAucunContexteOuvert() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> utilisateurService.listerParEtablissementCourant(pageable))
                .isInstanceOf(ContexteEtablissementAbsentException.class);
    }
}
