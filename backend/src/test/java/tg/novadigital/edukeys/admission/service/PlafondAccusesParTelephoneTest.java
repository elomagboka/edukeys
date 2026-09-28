package tg.novadigital.edukeys.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import tg.novadigital.edukeys.admission.AdmissionProperties;
import tg.novadigital.edukeys.common.notification.Notificateur;
import tg.novadigital.edukeys.common.notification.TypeNotification;

/**
 * I2 (revue US-06) : plafond d'accusés par numéro et par jour. Un SMS coûte
 * de l'argent, et le compteur par téléphone du filtre de débit ne protège
 * rien (une soumission réussie le remet à zéro, et la clé s'esquive). Sans
 * ce plafond, un attaquant muni de jetons Turnstile déclenche autant de SMS
 * qu'il veut vers le numéro d'une victime en changeant le nom de l'enfant à
 * chaque envoi : la facture est pour Nova Digital.
 *
 * <p>Le compteur vit en mémoire : ces tests portent sur une seule instance,
 * ce qui correspond au déploiement Render actuel.</p>
 */
class PlafondAccusesParTelephoneTest {

    private static final String TELEPHONE = "+22890000001";

    private PlafondAccusesParTelephone plafondDe(int maximumParJour) {
        AdmissionProperties proprietes = new AdmissionProperties();
        proprietes.setMaxAccusesParTelephoneParJour(maximumParJour);
        return new PlafondAccusesParTelephone(proprietes);
    }

    @Test
    void autoriseJusquAuPlafond_puisRefuseLesEnvoisSuivants() {
        PlafondAccusesParTelephone plafond = plafondDe(3);

        assertThat(plafond.autoriserEtCompter(TELEPHONE)).isTrue();
        assertThat(plafond.autoriserEtCompter(TELEPHONE)).isTrue();
        assertThat(plafond.autoriserEtCompter(TELEPHONE)).isTrue();

        assertThat(plafond.autoriserEtCompter(TELEPHONE)).isFalse();
        assertThat(plafond.autoriserEtCompter(TELEPHONE)).isFalse();
    }

    /** Le plafond compte les envois réussis, pas seulement les échecs — c'est là que le filtre de débit se trompait. */
    @Test
    void compteLesEnvoisReussis_etNonSeulementLesEchecs() {
        PlafondAccusesParTelephone plafond = plafondDe(1);

        assertThat(plafond.autoriserEtCompter(TELEPHONE)).isTrue();
        assertThat(plafond.autoriserEtCompter(TELEPHONE)).isFalse();
    }

    @Test
    void compteChaqueNumeroSeparement() {
        PlafondAccusesParTelephone plafond = plafondDe(1);

        assertThat(plafond.autoriserEtCompter("+22890000001")).isTrue();
        assertThat(plafond.autoriserEtCompter("+22890000002")).isTrue();
        assertThat(plafond.autoriserEtCompter("+22890000001")).isFalse();
    }

    /** Sans téléphone, rien à plafonner : l'accusé par email seul reste envoyé. */
    @Test
    void nEmpecheRien_quandLeTelephoneEstAbsent() {
        PlafondAccusesParTelephone plafond = plafondDe(1);

        assertThat(plafond.autoriserEtCompter(null)).isTrue();
        assertThat(plafond.autoriserEtCompter("  ")).isTrue();
    }

    /**
     * Le plafond doit être branché : c'est le listener qui décide d'envoyer,
     * et lui seul coûte de l'argent. Retirer l'appel au plafond dans
     * {@link NotificationAccuseReceptionAdmissionListener} fait tomber ce
     * test, alors que les tests ci-dessus resteraient verts.
     */
    @Test
    void leListenerCesseDenvoyer_auDelaDuPlafond() {
        Notificateur notificateur = mock(Notificateur.class);
        var listener = new NotificationAccuseReceptionAdmissionListener(notificateur, plafondDe(2));

        for (int i = 0; i < 5; i++) {
            listener.surSoumission(new DemandeAdmissionSoumiseEvent(
                    UUID.randomUUID(), "PRE-2026-00000" + i, TELEPHONE, "responsable@example.com"));
        }

        verify(notificateur, times(2)).envoyer(any(), eq(TypeNotification.ADMISSION_ACCUSE_RECEPTION), any());
    }

    @Test
    void leListenerNenvoieRien_desLePremierAccuse_quandLePlafondEstEpuise() {
        Notificateur notificateur = mock(Notificateur.class);
        PlafondAccusesParTelephone plafond = plafondDe(1);
        plafond.autoriserEtCompter(TELEPHONE);
        var listener = new NotificationAccuseReceptionAdmissionListener(notificateur, plafond);

        listener.surSoumission(new DemandeAdmissionSoumiseEvent(
                UUID.randomUUID(), "PRE-2026-000099", TELEPHONE, null));

        verify(notificateur, never()).envoyer(any(), any(), any());
    }
}
