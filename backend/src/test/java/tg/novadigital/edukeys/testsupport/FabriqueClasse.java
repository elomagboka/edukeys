package tg.novadigital.edukeys.testsupport;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;
import tg.novadigital.edukeys.etablissement.domain.Site;

/**
 * Fabrique de test de {@link Classe} (US-02, D2/D3). Relations obligatoires
 * {@code niveau} et {@code anneeScolaire}, plus une colonne scalaire
 * {@code siteId} contrainte par une vraie FK en base (D3) : les trois sont
 * persistées via {@link FabriqueSupport} avant de construire la classe.
 */
public class FabriqueClasse implements FabriqueEntiteEtablissement<Classe> {

    @Override
    public Class<Classe> typeEntite() {
        return Classe.class;
    }

    @Override
    public Classe creer(UUID etablissementId) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Cycle cycle = new Cycle(null, "ISOLATION-CYCLE-" + suffixe, null, ThreadLocalRandom.current().nextInt(1, 1_000_000));
        Niveau niveau = new Niveau(null, "ISOLATION-NIVEAU-" + suffixe, null,
                ThreadLocalRandom.current().nextInt(1, 1_000_000), cycle);
        int anneeDebut = 2000 + ThreadLocalRandom.current().nextInt(0, 1000);
        AnneeScolaire anneeScolaire = new AnneeScolaire(null, "ISOL-" + suffixe,
                LocalDate.of(anneeDebut, 9, 1), LocalDate.of(anneeDebut + 1, 7, 15));
        Site site = new Site(null, "ISOL-" + suffixe, "Site isolation " + suffixe, false, "Lomé", null, null, null);

        // Portée dédiée (indépendante de tout contexte éventuellement déjà
        // ouvert par l'appelant) : certains scénarios du test générique (C5)
        // appellent creer(...) sans aucun contexte ouvert.
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(cycle);
            entityManager.persist(niveau);
            entityManager.persist(anneeScolaire);
            entityManager.persist(site);
            entityManager.flush();
        }

        return new Classe(null, "ISOLATION-CLASSE-" + suffixe, null, niveau, null, anneeScolaire, site.getId(), null);
    }
}
