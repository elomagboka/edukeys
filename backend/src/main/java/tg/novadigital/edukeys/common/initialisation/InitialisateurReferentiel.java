package tg.novadigital.edukeys.common.initialisation;

import java.util.UUID;

/**
 * Étape d'initialisation du référentiel pédagogique d'un établissement
 * nouvellement créé (US-00). Implémentée par le module {@code academique}
 * (cycles, niveaux, filières, matières — US-02/03/05), jamais par
 * {@code common} lui-même : cette interface n'est qu'un point d'extension,
 * conformément à la règle « aucune dépendance croisée entre modules métier »
 * (CLAUDE.md, règle 1) — les échanges inter-modules passent par une
 * interface exposée dans le module fournisseur, ici {@code common}.
 *
 * <p>{@code EtablissementService} injecte {@code List<InitialisateurReferentiel>}
 * et appelle chaque implémentation, dans la même transaction que la création
 * de l'établissement (voir sa Javadoc) — une liste vide est acceptable tant
 * qu'aucun module ne fournit d'implémentation concrète (T-10).</p>
 */
public interface InitialisateurReferentiel {

    void initialiser(UUID etablissementId, ReferentielType modele);
}
