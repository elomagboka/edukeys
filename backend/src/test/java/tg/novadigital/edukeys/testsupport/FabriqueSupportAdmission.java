package tg.novadigital.edukeys.testsupport;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PorteeEtablissement;

/**
 * Aide partagée par les fabriques du module {@code admission} (3e revue,
 * point 6) : {@code demandes_admission.annee_scolaire_id} et {@code .niveau_id}
 * portent désormais une clé étrangère (V13) — une année scolaire et un niveau
 * réels doivent être persistés avant tout dossier de test, comme
 * {@link FabriqueNiveau} le fait déjà pour son cycle.
 */
final class FabriqueSupportAdmission {

    private FabriqueSupportAdmission() {
    }

    static UUID persisterAnneeScolaireId(UUID etablissementId, String suffixe) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        int anneeDebut = 2000 + ThreadLocalRandom.current().nextInt(0, 1000);
        LocalDate debut = LocalDate.of(anneeDebut, 9, 1);
        LocalDate fin = LocalDate.of(anneeDebut + 1, 7, 15);
        AnneeScolaire anneeScolaire = new AnneeScolaire(null, "ADM-ANNEE-" + suffixe, debut, fin);
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(anneeScolaire);
            entityManager.flush();
        }
        return anneeScolaire.getId();
    }

    static UUID persisterNiveauId(UUID etablissementId, String suffixe) {
        EntityManager entityManager = FabriqueSupport.entityManager();
        Cycle cycle = new Cycle(null, "ADM-CYCLE-" + suffixe, null, ThreadLocalRandom.current().nextInt(1, 1_000_000));
        Niveau niveau;
        try (PorteeEtablissement portee = ContexteEtablissement.ouvrir(etablissementId)) {
            entityManager.persist(cycle);
            entityManager.flush();
            niveau = new Niveau(null, "ADM-NIVEAU-" + suffixe, null, ThreadLocalRandom.current().nextInt(1, 1_000_000), cycle);
            entityManager.persist(niveau);
            entityManager.flush();
        }
        return niveau.getId();
    }
}
