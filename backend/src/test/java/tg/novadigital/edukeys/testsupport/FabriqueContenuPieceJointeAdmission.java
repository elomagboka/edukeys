package tg.novadigital.edukeys.testsupport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.admission.domain.CanalAdmission;
import tg.novadigital.edukeys.admission.domain.ContenuPieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.LienResponsable;
import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.domain.TypePieceAdmission;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Fabrique de test de {@link ContenuPieceJointeAdmission} (B3, revue) — sans
 * elle, l'entité échapperait silencieusement au périmètre
 * d'{@code IsolationEtablissementTest}. Sa clé étant partagée avec une
 * {@link PieceJointeAdmission} ({@code @MapsId}), celle-ci (et le dossier qui
 * la porte) sont persistées au préalable, comme {@code FabriquePieceJointeAdmission}
 * le fait déjà pour son dossier.
 */
public class FabriqueContenuPieceJointeAdmission implements FabriqueEntiteEtablissement<ContenuPieceJointeAdmission> {

    @Override
    public Class<ContenuPieceJointeAdmission> typeEntite() {
        return ContenuPieceJointeAdmission.class;
    }

    @Override
    public ContenuPieceJointeAdmission creer(UUID etablissementId) {
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

        PieceJointeAdmission piece;
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(demande);
            entityManager.flush();
            piece = new PieceJointeAdmission(
                    etablissementId,
                    demande.getId(),
                    TypePieceAdmission.ACTE_NAISSANCE,
                    "acte.pdf",
                    "application/pdf",
                    3,
                    "0".repeat(64));
            entityManager.persist(piece);
            entityManager.flush();
        }

        return new ContenuPieceJointeAdmission(null, piece, "0".repeat(64), new byte[]{1, 2, 3});
    }
}
