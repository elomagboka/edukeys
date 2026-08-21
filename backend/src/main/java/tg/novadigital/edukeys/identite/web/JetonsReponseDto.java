package tg.novadigital.edukeys.identite.web;

import java.util.List;
import java.util.UUID;

/**
 * Réponse commune à {@code /auth/login} et {@code /auth/refresh}.
 * {@code etablissementId} est l'UUID seul de l'établissement actif : l'entité
 * {@code Etablissement} (code, nom) n'existe pas encore, elle sera créée en
 * T-05 (arbitrage T-04 n°2).
 */
public record JetonsReponseDto(
        String accessToken,
        String refreshToken,
        long expiresDansSecondes,
        UUID etablissementId,
        List<String> roles) {
}
