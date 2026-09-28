package tg.novadigital.edukeys.testsupport;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.admission.domain.DecisionAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Fabrique de test de {@link DecisionAdmission} (US-07). {@code demandeId}
 * porte une FK {@code demandes_admission(id)} : un dossier minimal est donc
 * persisté au préalable, comme {@link FabriquePieceJointeAdmission}.
 */
public class FabriqueDecisionAdmission implements FabriqueEntiteEtablissement<DecisionAdmission> {

    @Override
    public Class<DecisionAdmission> typeEntite() {
        return DecisionAdmission.class;
    }

    @Override
    public DecisionAdmission creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        DemandeAdmission demande = new FabriqueDemandeAdmission().creer(etablissementId);

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(demande);
            entityManager.flush();
        }

        return new DecisionAdmission(
                null,
                demande.getId(),
                StatutAdmission.EN_ATTENTE,
                StatutAdmission.LISTE_ATTENTE,
                "Observation de test",
                UUID.randomUUID(),
                Instant.now());
    }
}
