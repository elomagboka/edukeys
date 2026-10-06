package tg.novadigital.edukeys.common.texte;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalisation partagée des noms pour comparaison (idempotence des dossiers
 * d'admission US-06, détection d'homonymes à l'inscription US-08) : une seule
 * définition, sans quoi un dossier et l'élève qui en naît ne se reconnaîtraient
 * plus. Calculée en Java, jamais par une fonction SQL (CLAUDE.md, règle 2).
 */
public final class NormalisationTexte {

    private NormalisationTexte() {
    }

    /** Sans accents (décomposition NFD, retrait des marques diacritiques), espaces de bord retirés, en majuscules. */
    public static String normaliserPourComparaison(String valeur) {
        if (valeur == null) {
            return null;
        }
        String sansAccents = Normalizer.normalize(valeur, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.trim().toUpperCase(Locale.ROOT);
    }
}
