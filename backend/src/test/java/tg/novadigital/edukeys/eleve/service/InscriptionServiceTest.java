package tg.novadigital.edukeys.eleve.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tg.novadigital.edukeys.academique.ClasseInscriptionQuery;

/** Garde-fou de {@link InscriptionService} : refus d'un appel depuis la transaction d'un appelant (US-08). */
class InscriptionServiceTest {

    @AfterEach
    void restaurer() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void refuseUnAppelDepuisUneTransactionActive_sansRienFaire() {
        ClasseInscriptionQuery classeQuery = mock(ClasseInscriptionQuery.class);
        CreateurLigneCompteurMatricule createur = mock(CreateurLigneCompteurMatricule.class);
        InscriptionTransactionnelle transactionnelle = mock(InscriptionTransactionnelle.class);
        InscriptionService service = new InscriptionService(classeQuery, createur, transactionnelle);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> service.inscrire(new CommandeInscription(UUID.randomUUID(), UUID.randomUUID(), 1, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hors de la transaction de l'appelant");
        verifyNoInteractions(classeQuery, createur, transactionnelle);
    }
}
