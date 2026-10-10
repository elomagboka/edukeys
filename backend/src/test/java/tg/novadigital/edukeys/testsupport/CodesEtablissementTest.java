package tg.novadigital.edukeys.testsupport;

import java.util.Locale;
import java.util.UUID;

/**
 * Codes d'établissement de test valides (US-08, contrainte {@code ck_etablissements_code_format} :
 * 2 à 10 majuscules ou chiffres). Dérivés de l'identifiant de l'établissement pour rester uniques sans
 * tirage aléatoire (l'index de code actif interdit les doublons).
 */
public final class CodesEtablissementTest {

    private CodesEtablissementTest() {
    }

    /** {@code prefixe} (lettres ou chiffres, 3 au plus) suivi des 7 premiers caractères hexadécimaux de l'identifiant : 10 caractères. */
    public static String code(String prefixe, UUID etablissementId) {
        String hex = etablissementId.toString().replace("-", "").toUpperCase(Locale.ROOT);
        String p = prefixe.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return (p.length() > 3 ? p.substring(0, 3) : p) + hex.substring(hex.length() - 7);
    }
}
