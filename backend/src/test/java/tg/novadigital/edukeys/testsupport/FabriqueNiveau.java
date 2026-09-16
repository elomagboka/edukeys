package tg.novadigital.edukeys.testsupport;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Fabrique de test de {@link Niveau} (US-02). Relation {@code cycle}
 * obligatoire : persiste un {@link Cycle} dédié via {@link FabriqueSupport}
 * avant de construire le niveau, sans quoi la contrainte de clé étrangère
 * {@code niveaux.cycle_id} échouerait au flush.
 */
public class FabriqueNiveau implements FabriqueEntiteEtablissement<Niveau> {

    @Override
    public Class<Niveau> typeEntite() {
        return Niveau.class;
    }

    @Override
    public Niveau creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Cycle cycle = new Cycle(null, "ISOLATION-CYCLE-" + suffixe, null, ThreadLocalRandom.current().nextInt(1, 1_000_000));
        // Portée dédiée (indépendante de tout contexte éventuellement déjà
        // ouvert par l'appelant) : certains scénarios du test générique (C5)
        // appellent creer(...) sans aucun contexte ouvert.
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(cycle);
            entityManager.flush();
        }

        return new Niveau(null, "ISOLATION-NIVEAU-" + suffixe, null,
                ThreadLocalRandom.current().nextInt(1, 1_000_000), cycle);
    }
}
