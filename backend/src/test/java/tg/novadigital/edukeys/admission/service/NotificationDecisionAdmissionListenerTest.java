package tg.novadigital.edukeys.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.common.notification.Notificateur;
import tg.novadigital.edukeys.common.notification.TypeNotification;

/**
 * Notification de décision d'admission (US-07) : bon type par statut, jamais
 * l'observation (note interne), exception avalée après commit (jamais
 * remontée à l'appelant).
 */
class NotificationDecisionAdmissionListenerTest {

    private final Notificateur notificateur = mock(Notificateur.class);
    private final NotificationDecisionAdmissionListener listener = new NotificationDecisionAdmissionListener(notificateur);

    @Test
    void doitNotifierAvecLeTypeAcceptee() {
        UUID demandeId = UUID.randomUUID();
        listener.surDecision(new DecisionAdmissionPriseEvent(demandeId, StatutAdmission.ACCEPTEE, "CS-000001", "+22890000001", "parent@example.com"));

        ArgumentCaptor<Map<String, Object>> donneesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificateur).envoyer(eq(demandeId), eq(TypeNotification.ADMISSION_DECISION_ACCEPTEE), donneesCaptor.capture());
        assertThat(donneesCaptor.getValue()).containsEntry("codeSuivi", "CS-000001");
        assertThat(donneesCaptor.getValue()).doesNotContainKey("observation");
    }

    @Test
    void doitNotifierAvecLeTypeRefusee() {
        UUID demandeId = UUID.randomUUID();
        listener.surDecision(new DecisionAdmissionPriseEvent(demandeId, StatutAdmission.REFUSEE, "CS-000002", "+22890000001", null));

        verify(notificateur).envoyer(eq(demandeId), eq(TypeNotification.ADMISSION_DECISION_REFUSEE), any());
    }

    @Test
    void doitNotifierAvecLeTypeListeAttente() {
        UUID demandeId = UUID.randomUUID();
        listener.surDecision(new DecisionAdmissionPriseEvent(demandeId, StatutAdmission.LISTE_ATTENTE, "CS-000003", "+22890000001", null));

        verify(notificateur).envoyer(eq(demandeId), eq(TypeNotification.ADMISSION_DECISION_LISTE_ATTENTE), any());
    }

    @Test
    void neDoitJamaisRemonterUneExceptionDuNotificateur() {
        UUID demandeId = UUID.randomUUID();
        doThrow(new RuntimeException("panne fournisseur SMS")).when(notificateur).envoyer(any(), any(), any());

        listener.surDecision(new DecisionAdmissionPriseEvent(demandeId, StatutAdmission.ACCEPTEE, "CS-000004", "+22890000001", null));
        // Aucune exception ne doit remonter : le test réussit s'il atteint cette ligne.
    }
}
