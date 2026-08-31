package tg.novadigital.edukeys.testsupport;

import java.util.UUID;

import tg.novadigital.edukeys.etablissement.domain.Site;

/**
 * Fabrique de test de {@link Site}, enregistrée dans {@link FabriquesEntitesTest}
 * (US-00, T-10) : sans elle, {@code Site} sortirait silencieusement du
 * périmètre d'{@code IsolationEtablissementTest} — exactement le scénario que
 * ce verrou existe pour empêcher.
 *
 * <p>Contenu constant, indépendant du paramètre {@code etablissementId}
 * (même convention que {@link FabriqueDemoEntite}) : {@code creer(...)}
 * renvoie une instance <b>transitoire, au champ {@code etablissementId} nul</b> —
 * c'est {@link tg.novadigital.edukeys.common.domain.RemplisseurEtablissement}
 * qui le remplit depuis le contexte ouvert au moment de la persistance
 * (R4.1), jamais la fabrique elle-même (R4.3 est exercé séparément, en
 * forçant le champ par réflexion). Site non principal, pour ne pas interférer
 * avec R4 (un seul site principal actif par établissement).</p>
 */
public class FabriqueSite implements FabriqueEntiteEtablissement<Site> {

    @Override
    public Class<Site> typeEntite() {
        return Site.class;
    }

    @Override
    public Site creer(UUID etablissementId) {
        // Code unique par instance (contrairement à FabriqueDemoEntite, sans
        // contrainte d'unicité) : R6 impose un code unique par établissement
        // parmi les sites actifs, et certains cas du test générique persistent
        // plusieurs sites dans le même établissement (ex. C1, C4).
        String code = "ISOLATION-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Site(null, code, "Site Isolation Test", false, "Lomé", null, null, null);
    }
}
