package tg.novadigital.edukeys.testsupport;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import tg.novadigital.edukeys.academique.domain.AnneeScolaire;

/**
 * Fabrique de test d'{@link AnneeScolaire}, enregistrée dans
 * {@link FabriquesEntitesTest} (US-01) : sans elle, {@code AnneeScolaire}
 * sortirait silencieusement du périmètre d'{@code IsolationEtablissementTest}.
 *
 * <p>Libellé et plage de dates aléatoires à chaque appel (contrairement à
 * {@link FabriqueSite}) : R4 (libellé unique par établissement parmi les
 * années actives) et la contrainte d'exclusion sur les plages de dates
 * (R5) interdisent tout doublon lorsque le test générique persiste plusieurs
 * instances dans le même établissement.</p>
 */
public class FabriqueAnneeScolaire implements FabriqueEntiteEtablissement<AnneeScolaire> {

    @Override
    public Class<AnneeScolaire> typeEntite() {
        return AnneeScolaire.class;
    }

    @Override
    public AnneeScolaire creer(UUID etablissementId) {
        int anneeDebut = 2000 + ThreadLocalRandom.current().nextInt(0, 1000);
        LocalDate dateDebut = LocalDate.of(anneeDebut, 9, 1);
        LocalDate dateFin = LocalDate.of(anneeDebut + 1, 7, 15);
        String libelle = "ISOLATION-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new AnneeScolaire(null, libelle, dateDebut, dateFin);
    }
}
