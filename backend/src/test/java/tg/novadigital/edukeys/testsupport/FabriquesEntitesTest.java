package tg.novadigital.edukeys.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registre des fabriques de test des entités {@code EntiteEtablissement}
 * (T-05, sous-tâche 10). Toute nouvelle entité métier étendant
 * {@code EntiteEtablissement} doit y enregistrer une fabrique : c'est ce
 * registre que consomme le test d'isolation générique (sous-tâche 11,
 * agent {@code testeur}) pour vérifier, entité par entité et via le
 * metamodel JPA, qu'un utilisateur de l'établissement A ne peut atteindre
 * aucune donnée de l'établissement B — y compris par accès direct à
 * l'identifiant (ADR-0002, « risque principal et sa parade »).
 *
 * <p>Deux établissements systématiquement disponibles (ADR-0002, impact
 * T-08 : « les fabriques de test créent systématiquement deux
 * établissements »), plutôt qu'un seul + un généré à la volée dans chaque
 * test : un seul jeu d'identifiants partagé simplifie l'écriture du test
 * d'isolation générique, qui doit comparer le même couple pour toutes les
 * entités du registre.</p>
 */
public final class FabriquesEntitesTest {

    /** Deux établissements de test, systématiquement utilisés par les fabriques (ADR-0002 / T-08). */
    public static final UUID ETABLISSEMENT_A = UUID.randomUUID();
    public static final UUID ETABLISSEMENT_B = UUID.randomUUID();

    private static final List<FabriqueEntiteEtablissement<?>> FABRIQUES = new ArrayList<>();

    static {
        FABRIQUES.add(new FabriqueDemoEntite());
        FABRIQUES.add(new FabriqueSite());
        FABRIQUES.add(new FabriqueLogoEtablissement());
    }

    private FabriquesEntitesTest() {
    }

    /** Toutes les fabriques enregistrées, dans l'ordre d'enregistrement. */
    public static List<FabriqueEntiteEtablissement<?>> toutes() {
        return List.copyOf(FABRIQUES);
    }
}
