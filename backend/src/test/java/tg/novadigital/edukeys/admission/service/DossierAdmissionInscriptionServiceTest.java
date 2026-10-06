package tg.novadigital.edukeys.admission.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.repository.DemandeAdmissionRepository;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;

class DossierAdmissionInscriptionServiceTest {

    private final DemandeAdmissionRepository repository = mock(DemandeAdmissionRepository.class);
    private final DossierAdmissionInscriptionService service = new DossierAdmissionInscriptionService(repository);

    private DemandeAdmission demande(UUID etablissementId, boolean actif) {
        DemandeAdmission d = mock(DemandeAdmission.class);
        when(d.getEtablissementId()).thenReturn(etablissementId);
        when(d.isActif()).thenReturn(actif);
        return d;
    }

    @Test
    void marquerInscrite_neRefaitPasDeSelectForUpdate_etMarqueLeDossierDeLaSession() {
        UUID etab = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID eleve = UUID.randomUUID();
        Instant instant = Instant.parse("2026-09-01T08:00:00Z");
        DemandeAdmission d = demande(etab, true);
        when(repository.findById(id)).thenReturn(Optional.of(d));

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            service.marquerInscrite(id, eleve, instant);
        }

        verify(d).marquerInscrite(eleve, instant);
        verify(repository, never()).trouverPourVerrouiller(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void marquerInscrite_refuseUnDossierDUnAutreEtablissementOuInactif() {
        UUID etab = UUID.randomUUID();
        UUID autre = UUID.randomUUID();
        UUID inactif = UUID.randomUUID();
        DemandeAdmission dAutre = demande(UUID.randomUUID(), true);
        DemandeAdmission dInactif = demande(etab, false);
        when(repository.findById(autre)).thenReturn(Optional.of(dAutre));
        when(repository.findById(inactif)).thenReturn(Optional.of(dInactif));

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            assertThatThrownBy(() -> service.marquerInscrite(autre, UUID.randomUUID(), Instant.now()))
                    .isInstanceOf(RessourceIntrouvableException.class);
            assertThatThrownBy(() -> service.marquerInscrite(inactif, UUID.randomUUID(), Instant.now()))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
        verify(dAutre, never()).marquerInscrite(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
