package tg.novadigital.edukeys.testsupport;

import java.util.UUID;

import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Contrat d'une fabrique de test pour une entité {@link EntiteEtablissement}
 * (T-05, sous-tâche 10). Une fabrique produit une instance transitoire
 * (non persistée) rattachable à l'établissement donné : c'est à l'appelant
 * (le repository correspondant, à l'intérieur d'une portée
 * {@code ContexteEtablissement.ouvrir(etablissementId)}) de la persister,
 * pour que {@code RemplisseurEtablissement} confirme cet établissement.
 *
 * @param <T> type de l'entité produite
 */
public interface FabriqueEntiteEtablissement<T extends EntiteEtablissement> {

    Class<T> typeEntite();

    T creer(UUID etablissementId);
}
