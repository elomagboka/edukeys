package tg.novadigital.edukeys.common.securite;

import java.util.Locale;

/**
 * Normalisation unique de l'identifiant de connexion (email du personnel,
 * matricule d'un élève) : trim + minuscules ({@link Locale#ROOT}, jamais la
 * locale par défaut). Utilisée à l'écriture ({@code Utilisateur}), à la
 * recherche ({@code AuthService}) et pour la clé de limitation de débit
 * ({@code FiltreLimitationDebit}) : une seule définition, sans quoi un compte
 * pourrait échapper au compteur en variant la casse.
 *
 * <p>Côté SQL (migration V15, trigger de rattrapage), l'équivalent est
 * {@code lower(btrim(valeur))}. {@code String.trim()} retire tout caractère
 * &lt;= U+0020 alors que {@code btrim} ne retire que les espaces : la différence
 * ne porte que sur des caractères de contrôle, qu'un email validé ne contient
 * pas.</p>
 */
public final class IdentifiantConnexion {

    private IdentifiantConnexion() {
    }

    /** @return la valeur normalisée, ou {@code null} si {@code identifiant} est {@code null}. */
    public static String normaliser(String identifiant) {
        return identifiant == null ? null : identifiant.trim().toLowerCase(Locale.ROOT);
    }
}
