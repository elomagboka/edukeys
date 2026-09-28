package tg.novadigital.edukeys.testsupport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.admission.domain.CanalAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.LienResponsable;
import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.TypePieceAdmission;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Fabrique de test de {@link PieceJointeAdmission} (US-06). {@code demandeId}
 * porte une FK {@code demandes_admission(id)} : un dossier minimal est donc
 * persisté au préalable, comme {@code FabriquePeriodeAcademique} le fait pour
 * son année scolaire.
 */
public class FabriquePieceJointeAdmission implements FabriqueEntiteEtablissement<PieceJointeAdmission> {

    @Override
    public Class<PieceJointeAdmission> typeEntite() {
        return PieceJointeAdmission.class;
    }

    @Override
    public PieceJointeAdmission creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID anneeScolaireId = FabriqueSupportAdmission.persisterAnneeScolaireId(etablissementId, suffixe);
        UUID niveauId = FabriqueSupportAdmission.persisterNiveauId(etablissementId, suffixe);
        DemandeAdmission demande = new DemandeAdmission(
                etablissementId,
                "PRE-ISO-" + suffixe,
                "CODESUIVI-ISO-" + suffixe,
                anneeScolaireId,
                niveauId,
                null,
                "Nom" + suffixe,
                "Prenoms" + suffixe,
                LocalDate.of(2015, 1, 1),
                "Lomé",
                "F",
                "TG",
                null,
                "Responsable",
                "Test",
                LienResponsable.PERE,
                "+22890000000",
                "responsable@example.com",
                CanalAdmission.PUBLIC,
                Instant.now(),
                Instant.now(),
                "0".repeat(64));

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(demande);
            entityManager.flush();
        }

        return new PieceJointeAdmission(
                null,
                demande.getId(),
                TypePieceAdmission.ACTE_NAISSANCE,
                "acte.pdf",
                "application/pdf",
                10,
                "0".repeat(64));
    }
}
