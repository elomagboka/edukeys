package tg.novadigital.edukeys.testsupport;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import tg.novadigital.edukeys.academique.domain.Cycle;

/**
 * Fabrique de test de {@link Cycle}, enregistrée dans {@link FabriquesEntitesTest}
 * (US-02) : sans elle, {@code Cycle} sortirait silencieusement du périmètre
 * d'{@code IsolationEtablissementTest}. Rang aléatoire (même raisonnement que
 * {@link FabriqueAnneeScolaire} sur les dates) : unique par établissement
 * parmi les actifs, plusieurs instances sont persistées dans le même
 * établissement par le test générique (C1, C4).
 */
public class FabriqueCycle implements FabriqueEntiteEtablissement<Cycle> {

    @Override
    public Class<Cycle> typeEntite() {
        return Cycle.class;
    }

    @Override
    public Cycle creer(UUID etablissementId) {
        String suffixe = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        int rang = ThreadLocalRandom.current().nextInt(1, 1_000_000);
        return new Cycle(null, "ISOLATION-CYCLE-" + suffixe, null, rang);
    }
}
