package tg.novadigital.edukeys.admission.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;

/**
 * Génère le code de suivi opaque d'un dossier d'admission (3e revue, point 1) :
 * 128 bits d'aléatoire cryptographique ({@link SecureRandom}), encodés en
 * Crockford Base32 (sans caractères ambigus {@code I}, {@code L}, {@code O},
 * {@code U}) — jamais un UUID v7 (horodaté, donc ordonnable : il rouvrirait la
 * même fuite qu'une référence séquentielle), jamais {@code Math.random}, jamais
 * dérivé d'un compteur ou d'un identifiant existant.
 *
 * <p>Contrairement à {@link GenerateurReferenceAdmission}, aucune notion
 * d'unicité applicative à vérifier : la probabilité de collision sur 128 bits
 * est négligeable (l'index unique partiel posé en V13 reste le filet de
 * sécurité, comme pour toute autre contrainte d'unicité de ce module).</p>
 */
@Service
public class GenerateurCodeSuiviAdmission {

    /** Alphabet de Crockford (32 symboles) : exclut I, L, O, U pour éviter toute confusion à la lecture ou à la saisie. */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int OCTETS_ALEATOIRES = 16; // 128 bits.

    private final SecureRandom secureRandom = new SecureRandom();

    public String genererCodeSuivi() {
        byte[] octets = new byte[OCTETS_ALEATOIRES];
        secureRandom.nextBytes(octets);
        return encoderEnBase32(octets);
    }

    private static String encoderEnBase32(byte[] donnees) {
        StringBuilder resultat = new StringBuilder();
        int tamponBits = 0;
        int nombreBits = 0;
        for (byte octet : donnees) {
            tamponBits = (tamponBits << 8) | (octet & 0xFF);
            nombreBits += 8;
            while (nombreBits >= 5) {
                resultat.append(ALPHABET[(tamponBits >>> (nombreBits - 5)) & 0x1F]);
                nombreBits -= 5;
            }
        }
        if (nombreBits > 0) {
            resultat.append(ALPHABET[(tamponBits << (5 - nombreBits)) & 0x1F]);
        }
        return resultat.toString();
    }
}
