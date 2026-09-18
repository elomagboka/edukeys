package tg.novadigital.edukeys.testsupport;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AffectationMatiere;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Matiere;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Fabrique de test de {@link AffectationMatiere} (US-03). Relations
 * obligatoires {@code matiere} et {@code niveau} persistées via
 * {@link FabriqueSupport}, sur le même principe que {@link FabriqueClasse}.
 * {@code filiere} volontairement absente (nullable, D4/US-03) : état minimal
 * suffisant pour l'isolation.
 */
public class FabriqueAffectationMatiere implements FabriqueEntiteEtablissement<AffectationMatiere> {

    @Override
    public Class<AffectationMatiere> typeEntite() {
        return AffectationMatiere.class;
    }

    @Override
    public AffectationMatiere creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Cycle cycle = new Cycle(null, "ISOLATION-CYCLE-" + suffixe, null, ThreadLocalRandom.current().nextInt(1, 1_000_000));
        Niveau niveau = new Niveau(null, "ISOLATION-NIVEAU-" + suffixe, null,
                ThreadLocalRandom.current().nextInt(1, 1_000_000), cycle);
        Matiere matiere = new Matiere(null, "ISOLATION-MATIERE-" + suffixe, null);

        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(cycle);
            entityManager.persist(niveau);
            entityManager.persist(matiere);
            entityManager.flush();
        }

        return new AffectationMatiere(null, matiere, niveau, null, null, null, null);
    }
}
