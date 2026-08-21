package tg.novadigital.edukeys.identite.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Génère et hache les refresh tokens opaques. Un refresh token n'est jamais
 * un JWT ni stocké en clair — comparaison par hachage, comme un mot de passe
 * (arbitrage T-04 n°3). SHA-256 suffit ici : contrairement à un mot de passe,
 * un refresh token est une chaîne aléatoire à haute entropie, pas un secret
 * choisi par un humain.
 */
@Component
public class JetonHacheur {

    private final SecureRandom generateurAleatoire = new SecureRandom();

    public String genererJetonEnClair() {
        byte[] octets = new byte[64];
        generateurAleatoire.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    public String hacher(String jetonEnClair) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hache = digest.digest(jetonEnClair.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hache);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithme de hachage indisponible.", e);
        }
    }
}
