package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

/**
 * Motif d'itération pour tâche planifiée (sous-tâche 9, T-05) : un batch
 * n'a, par construction, aucun contexte d'établissement ouvert
 * (ADR-0002, §2, deuxième angle mort — US-25 clôture de caisse, US-28
 * notifications). La règle : itérer explicitement sur les établissements
 * actifs et ouvrir une portée par établissement, jamais un traitement qui
 * agrège au-delà d'un établissement à la fois.
 *
 * <p>Ce test documente le motif attendu de tout futur {@code @Scheduled} qui
 * touche des entités {@link tg.novadigital.edukeys.common.domain.EntiteEtablissement} —
 * il n'appelle aucun repository réel (ce socle {@code common} ne dépend
 * d'aucun module métier), seulement le motif d'ouverture de portée.</p>
 */
class MotifIterationTachePlanifieeTest {

    /**
     * Traitement fictif représentant le corps d'une tâche planifiée future
     * (ex. US-25 : clôture de caisse quotidienne). En conditions réelles,
     * {@code etablissementsActifsIds()} vient d'un repository
     * {@code EtablissementRepository.findAll()} filtré sur {@code actif}.
     */
    private static List<UUID> executerTraitementPlanifie(List<UUID> etablissementsActifsIds) {
        List<UUID> etablissementsTraites = new CopyOnWriteArrayList<>();
        for (UUID etablissementId : etablissementsActifsIds) {
            // Une portée par établissement : jamais un contexte ouvert une
            // seule fois pour la boucle entière, ce qui agrégerait les
            // établissements entre eux dans une même session/transaction.
            try (var portee = ContexteEtablissement.ouvrir(etablissementId)) {
                etablissementsTraites.add(ContexteEtablissement.exigerEtablissementId());
            }
        }
        return etablissementsTraites;
    }

    @Test
    void ouvre_une_portee_distincte_par_etablissement_puis_ne_laisse_aucun_contexte_ouvert() {
        UUID etablissementA = UUID.randomUUID();
        UUID etablissementB = UUID.randomUUID();

        List<UUID> traites = executerTraitementPlanifie(List.of(etablissementA, etablissementB));

        assertThat(traites).containsExactly(etablissementA, etablissementB);
        assertThat(ContexteEtablissement.courant()).isEmpty();
    }
}
