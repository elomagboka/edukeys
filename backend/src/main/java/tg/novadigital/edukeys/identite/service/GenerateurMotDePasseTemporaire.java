package tg.novadigital.edukeys.identite.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Génère un mot de passe temporaire à usage unique (US-04), remis une seule
 * fois dans la réponse de création de compte — jamais relogué, jamais
 * renvoyé par une lecture ultérieure (seul son hash est conservé, voir
 * {@link tg.novadigital.edukeys.identite.domain.JetonActivationCompte}).
 *
 * <p>Alphabet sans caractères ambigus (ni {@code O/0}, {@code I/l/1}) : ce
 * mot de passe est destiné à être recopié à la main par un utilisateur qui
 * n'a pas forcément d'email fiable pour le recevoir (parent togolais, arbitrage
 * spec US-04 §2).</p>
 */
@Component
public class GenerateurMotDePasseTemporaire {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LONGUEUR = 12;

    private final SecureRandom generateurAleatoire = new SecureRandom();

    public String generer() {
        StringBuilder motDePasse = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) {
            motDePasse.append(ALPHABET.charAt(generateurAleatoire.nextInt(ALPHABET.length())));
        }
        return motDePasse.toString();
    }
}
