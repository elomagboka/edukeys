package tg.novadigital.edukeys.identite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.JetonRafraichissementRepository;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

class UtilisateurServiceTest {

    private UtilisateurRepository utilisateurRepository;
    private JetonRafraichissementRepository jetonRafraichissementRepository;
    private UtilisateurService utilisateurService;

    @BeforeEach
    void configurer() {
        utilisateurRepository = mock(UtilisateurRepository.class);
        jetonRafraichissementRepository = mock(JetonRafraichissementRepository.class);
        utilisateurService = new UtilisateurService(utilisateurRepository, jetonRafraichissementRepository);
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
}
