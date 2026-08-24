package tg.novadigital.edukeys.testsupport;

import java.util.UUID;

import tg.novadigital.edukeys.common.demo.domain.DemoEntite;

/**
 * Fabrique de test de {@link DemoEntite}, enregistrée dans {@link FabriquesEntitesTest}.
 *
 * <p>Contenu volontairement <strong>constant</strong>, indépendant de
 * {@code etablissementId} : c'est ce qui produit des données homonymes (même
 * libellé, même catégorie) dans les deux établissements de test quand
 * {@code creer(...)} est appelée une fois par établissement — condition
 * posée par {@code IsolationEtablissementTest} (T-05, sous-tâche 11) pour
 * qu'un test d'isolation ne puisse pas passer par hasard en discriminant sur
 * le contenu plutôt que sur l'établissement.</p>
 */
public class FabriqueDemoEntite implements FabriqueEntiteEtablissement<DemoEntite> {

    @Override
    public Class<DemoEntite> typeEntite() {
        return DemoEntite.class;
    }

    @Override
    public DemoEntite creer(UUID etablissementId) {
        return new DemoEntite("Homonyme", "test-isolation", 1);
    }
}
