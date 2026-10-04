package tg.novadigital.edukeys.common.securite;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Charge et valide la clé HMAC des empreintes de sécurité
 * ({@link JournalSecurite#empreinte}). Même règle que le secret JWT : aucune
 * valeur de repli, l'application refuse de démarrer si
 * {@code edukeys.securite.journal.cle-empreinte} (variable
 * {@code EDUKEYS_JOURNAL_CLE_EMPREINTE}) est absente ou trop courte. Les
 * profils {@code local} et {@code test} fournissent leur propre valeur.
 */
@Component
public class CleEmpreinteSecurite {

    static final int TAILLE_MINIMALE_OCTETS = 32;

    public CleEmpreinteSecurite(@Value("${edukeys.securite.journal.cle-empreinte:}") String cle) {
        byte[] octets = cle == null ? new byte[0] : cle.getBytes(StandardCharsets.UTF_8);
        if (octets.length < TAILLE_MINIMALE_OCTETS) {
            throw new IllegalStateException(
                    "edukeys.securite.journal.cle-empreinte est absent ou fait moins de "
                            + TAILLE_MINIMALE_OCTETS
                            + " octets : positionnez EDUKEYS_JOURNAL_CLE_EMPREINTE (ou le profil applicatif "
                            + "concerné) avant de démarrer l'application.");
        }
        JournalSecurite.configurerCle(octets);
    }
}
