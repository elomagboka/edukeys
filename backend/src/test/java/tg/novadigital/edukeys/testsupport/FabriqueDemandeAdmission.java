package tg.novadigital.edukeys.testsupport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import tg.novadigital.edukeys.admission.domain.CanalAdmission;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.LienResponsable;

/**
 * Fabrique de test de {@link DemandeAdmission} (US-06). {@code anneeScolaireId},
 * {@code niveauId} scalaires (pas de relation JPA vers {@code academique},
 * CLAUDE.md règle 1) — mais portant une FK en base depuis V13 (3e revue,
 * point 6) : une année scolaire et un niveau réels sont donc persistés au
 * préalable via {@link FabriqueSupportAdmission}.
 */
public class FabriqueDemandeAdmission implements FabriqueEntiteEtablissement<DemandeAdmission> {

    @Override
    public Class<DemandeAdmission> typeEntite() {
        return DemandeAdmission.class;
    }

    @Override
    public DemandeAdmission creer(UUID etablissementId) {
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // 3e revue, point 6 : niveau_id et annee_scolaire_id portent désormais
        // une FK -- des entités réelles doivent être persistées au préalable.
        UUID anneeScolaireId = FabriqueSupportAdmission.persisterAnneeScolaireId(etablissementId, suffixe);
        UUID niveauId = FabriqueSupportAdmission.persisterNiveauId(etablissementId, suffixe);
        return new DemandeAdmission(
                null,
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
    }
}
