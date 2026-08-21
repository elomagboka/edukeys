package tg.novadigital.edukeys.common.securite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Journalisation des évènements de sécurité (échecs d'authentification,
 * limitation de débit) sur un logger dédié {@code "SECURITE"}, séparé du
 * logger applicatif habituel pour permettre un routage ou une rétention
 * différents.
 *
 * <p>Ne journalise jamais un email ni un mot de passe en clair : un compte
 * existant est identifié par son UUID, un compte inconnu par une empreinte
 * SHA-256 tronquée de la valeur tentée — juste assez pour corréler des
 * tentatives répétées contre la même valeur, sans permettre de la retrouver
 * ni de l'énumérer. Voir {@code issue-rate-limiting.md} : le message d'erreur
 * HTTP reste identique quel que soit le motif interne.</p>
 */
public final class JournalSecurite {

    private static final Logger LOG = LoggerFactory.getLogger("SECURITE");

    private static final Pattern FORME_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int LONGUEUR_EMPREINTE = 12;

    private JournalSecurite() {
    }

    /** Échec d'authentification contre un compte existant (mot de passe incorrect, compte désactivé...). */
    public static void echecAuthentificationCompteExistant(UUID utilisateurId, String adresseIp, String motif) {
        LOG.warn("echec_authentification motif={} utilisateurId={} ip={} horodatage={}",
                motif, utilisateurId, adresseIp, Instant.now());
    }

    /**
     * Échec d'authentification quand aucun compte ne correspond à la valeur
     * tentée. Si la valeur n'a pas la forme d'une adresse email, aucune
     * empreinte n'est calculée : l'utilisateur a probablement interverti
     * email et mot de passe, et une empreinte du mot de passe faciliterait
     * une attaque par dictionnaire hors ligne.
     */
    public static void echecAuthentificationCompteInconnu(String valeurTentee, String adresseIp) {
        if (!ressembleAUnEmail(valeurTentee)) {
            LOG.warn("echec_authentification motif=format_invalide ip={} horodatage={}",
                    adresseIp, Instant.now());
            return;
        }

        LOG.warn("echec_authentification motif=compte_inconnu empreinte={} ip={} horodatage={}",
                empreinte(valeurTentee), adresseIp, Instant.now());
    }

    /** Échec dû à la limitation de débit (seuil par compte ou par IP dépassé). */
    public static void echecLimitationDebit(UUID utilisateurId, String adresseIp) {
        LOG.warn("echec_authentification motif=limitation_debit utilisateurId={} ip={} horodatage={}",
                utilisateurId, adresseIp, Instant.now());
    }

    /**
     * Jeton d'accès rejeté par {@code JwtAuthenticationFilter} (expiré,
     * signature invalide, malformé...). Ne journalise jamais le jeton
     * lui-même. Niveau différencié par motif : un jeton expiré après 15
     * minutes d'inactivité est un évènement attendu et fréquent (DEBUG), une
     * signature invalide ou un jeton malformé ne l'est jamais — c'est le
     * signal d'un balayage de jetons forgés, et un niveau DEBUG (souvent
     * désactivé en production) le rendrait invisible (relecture T-04,
     * repasse n°2).
     */
    public static void rejetJeton(String motif, String adresseIp) {
        if ("jeton_expire".equals(motif)) {
            LOG.debug("jeton_rejete motif={} ip={} horodatage={}", motif, adresseIp, Instant.now());
        } else {
            LOG.warn("jeton_rejete motif={} ip={} horodatage={}", motif, adresseIp, Instant.now());
        }
    }

    private static boolean ressembleAUnEmail(String valeur) {
        return valeur != null && FORME_EMAIL.matcher(valeur).matches();
    }

    private static String empreinte(String valeur) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hache = digest.digest(valeur.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hache).substring(0, LONGUEUR_EMPREINTE);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible.", e);
        }
    }
}
