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
 * CLAUDE.md règle 1) : aucune entité supplémentaire à persister.
 */
public class FabriqueDemandeAdmission implements FabriqueEntiteEtablissement<DemandeAdmission> {

    @Override
    public Class<DemandeAdmission> typeEntite() {
        return DemandeAdmission.class;
    }

    @Override
    public DemandeAdmission creer(UUID etablissementId) {
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new DemandeAdmission(
                null,
                "PRE-ISO-" + suffixe,
                UUID.randomUUID(),
                UUID.randomUUID(),
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
