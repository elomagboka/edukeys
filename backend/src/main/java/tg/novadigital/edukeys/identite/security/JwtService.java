package tg.novadigital.edukeys.identite.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Émission et vérification des access tokens JWT (15 minutes, stateless,
 * jamais vérifiés en base — arbitrage T-04 n°3). Porte {@code sub}
 * (utilisateur), {@code eta} (établissement actif) et {@code roles} (codes de
 * rôle cumulés sur l'affectation), jamais les permissions (arbitrage n°5).
 */
@Component
public class JwtService {

    public static final Duration DUREE_ACCESS_TOKEN = Duration.ofMinutes(15);
    private static final int TAILLE_MINIMALE_SECRET_OCTETS = 32;

    private final SecretKey cle;

    public JwtService(@Value("${edukeys.securite.jwt.secret:}") String secret) {
        byte[] octets = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < TAILLE_MINIMALE_SECRET_OCTETS) {
            throw new IllegalStateException(
                    "edukeys.securite.jwt.secret est absent ou fait moins de "
                            + TAILLE_MINIMALE_SECRET_OCTETS
                            + " octets : positionnez EDUKEYS_JWT_SECRET (ou le profil applicatif "
                            + "concerné) avant de démarrer l'application.");
        }
        this.cle = Keys.hmacShaKeyFor(octets);
    }

    public String genererAccessToken(UUID utilisateurId, UUID etablissementId, Set<String> codesRoles) {
        Instant maintenant = Instant.now();
        var builder = Jwts.builder()
                .subject(utilisateurId.toString())
                .claim("roles", codesRoles)
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(DUREE_ACCESS_TOKEN)))
                .signWith(cle);
        if (etablissementId != null) {
            builder.claim("eta", etablissementId.toString());
        }
        return builder.compact();
    }

    public UtilisateurPrincipal analyser(String accessToken) {
        Claims claims = Jwts.parser()
                .verifyWith(cle)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

        UUID utilisateurId = UUID.fromString(claims.getSubject());
        String etablissementBrut = claims.get("eta", String.class);
        UUID etablissementId = etablissementBrut != null ? UUID.fromString(etablissementBrut) : null;

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        Set<String> codesRoles = roles == null ? Set.of() : roles.stream().collect(Collectors.toUnmodifiableSet());

        return new UtilisateurPrincipal(utilisateurId, etablissementId, codesRoles);
    }

    public boolean estValide(String accessToken) {
        try {
            analyser(accessToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
