package tg.novadigital.edukeys.common.securite;

import java.util.regex.Pattern;

/**
 * Format du code d'un établissement (US-08) : 2 à 10 majuscules ou chiffres
 * ASCII, sans tiret (séparateur du matricule), accent ni espace. Le code entre
 * dans le matricule, définitif (bulletins, reçus), et dans l'identifiant de
 * connexion des élèves. Même définition que la contrainte
 * {@code ck_etablissements_code_format} (V17) : une seule source côté Java,
 * utilisée par le module {@code etablissement} (création) et par le générateur
 * de matricule (défense en profondeur).
 */
public final class CodeEtablissementFormat {

    /** Expression régulière, utilisable telle quelle dans une annotation {@code @Pattern} (après normalisation en majuscules). */
    public static final String EXPRESSION = "^[A-Z0-9]{2,10}$";

    /** Variante insensible à la casse pour la validation d'entrée : le service met le code en majuscules avant de le stocker. */
    public static final String EXPRESSION_SAISIE = "^[A-Za-z0-9]{2,10}$";

    private static final Pattern MOTIF = Pattern.compile(EXPRESSION);

    private CodeEtablissementFormat() {
    }

    public static boolean estValide(String code) {
        return code != null && MOTIF.matcher(code).matches();
    }
}
