package tg.novadigital.edukeys.common.securite.reseau;

import java.util.ArrayList;
import java.util.List;

/**
 * Détermine l'adresse IP réellement attribuable au client à partir de
 * l'en-tête {@code X-Forwarded-For}, en ne faisant confiance qu'aux éléments
 * ajoutés par les proxys que nous contrôlons.
 *
 * <p><strong>Pourquoi ne pas prendre le premier élément.</strong>
 * {@code X-Forwarded-For} est une liste que chaque intermédiaire complète
 * <em>par la droite</em> : le client peut donc écrire lui-même les éléments
 * de gauche. Prendre le premier élément — ce que fait le
 * {@code ForwardedHeaderFilter} de Spring, et donc
 * {@code request.getRemoteAddr()} sous
 * {@code server.forward-headers-strategy: framework} — revient à laisser
 * l'appelant choisir son identité réseau. Vérifié localement avant
 * correction : une requête portant {@code X-Forwarded-For: 1.2.3.4, 5.6.7.8}
 * était vue comme venant de {@code 1.2.3.4}, valeur entièrement écrite par le
 * client. Un compteur par IP adossé à cette valeur est contournable en une
 * ligne de curl, et permet en prime de faire bloquer l'adresse d'un tiers.</p>
 *
 * <p>On prend donc le <strong>N-ième élément en partant de la droite</strong>,
 * où N = {@code nbProxysDeConfiance} est le nombre d'intermédiaires que la
 * requête traverse chez nous (1 sur Render : l'edge). Les éléments à gauche
 * de celui-là sont, par construction, ceux que le client a pu forger : ils
 * sont ignorés. Ce calcul ne dépend d'aucune hypothèse sur le comportement de
 * l'edge — qu'il écrase l'en-tête reçu ou qu'il y ajoute sa valeur, le
 * résultat est le même, ce qui était précisément le point non vérifiable
 * jusqu'ici (issue #58).</p>
 *
 * <p>Si l'en-tête est absent ou porte moins d'éléments qu'attendu — le cas
 * normal en développement local, sans proxy — on retombe sur
 * {@code remoteAddr} plutôt que d'échouer.</p>
 */
public final class ExtracteurAdresseIpCliente {

    private ExtracteurAdresseIpCliente() {
    }

    /**
     * @param enTeteXForwardedFor valeur brute de {@code X-Forwarded-For}, ou {@code null}
     * @param adresseDistante     {@code request.getRemoteAddr()}, utilisé en repli
     * @param nbProxysDeConfiance nombre d'intermédiaires que nous contrôlons (≥ 1)
     */
    public static String extraire(String enTeteXForwardedFor, String adresseDistante, int nbProxysDeConfiance) {
        if (enTeteXForwardedFor == null || enTeteXForwardedFor.isBlank()) {
            return adresseDistante;
        }

        List<String> elements = new ArrayList<>();
        for (String element : enTeteXForwardedFor.split(",")) {
            String nettoye = element.trim();
            if (!nettoye.isEmpty()) {
                elements.add(nettoye);
            }
        }

        // Moins d'éléments que d'intermédiaires attendus : la requête n'a pas
        // traversé la chaîne de proxys prévue (appel direct, développement
        // local). Aucune valeur de l'en-tête n'est alors digne de confiance.
        int index = elements.size() - nbProxysDeConfiance;
        if (index < 0 || index >= elements.size()) {
            return adresseDistante;
        }
        return elements.get(index);
    }
}
