package tg.novadigital.edukeys.identite.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * {@link JwtService} est le point d'entrée de toute la sécurité T-04 : un
 * jeton expiré, mal signé ou manipulé doit être rejeté sans ambiguïté
 * (correction T-04, lot 2 n°7 — jusqu'ici entièrement non testé).
 */
class JwtServiceTest {

    private static final String SECRET_32_OCTETS_MINIMUM = "un-secret-de-test-qui-fait-bien-32-octets-ou-plus";
    private static final String AUTRE_SECRET_32_OCTETS_MINIMUM = "un-secret-totalement-different-32-octets-minimum";

    private final JwtService jwtService = new JwtService(SECRET_32_OCTETS_MINIMUM);

    @Test
    void genereEtAnalyseUnJeton_avecLesRevendicationsAttendues() {
        UUID utilisateurId = UUID.randomUUID();
        UUID etablissementId = UUID.randomUUID();

        String jeton = jwtService.genererAccessToken(utilisateurId, etablissementId, Set.of("DIRECTION", "ADMIN"));
        UtilisateurPrincipal principal = jwtService.analyser(jeton);

        assertThat(principal.utilisateurId()).isEqualTo(utilisateurId);
        assertThat(principal.etablissementId()).isEqualTo(etablissementId);
        assertThat(principal.codesRoles()).containsExactlyInAnyOrder("DIRECTION", "ADMIN");
    }

    @Test
    void analyseUnJetonSansEtablissement_avecEtablissementIdNull() {
        String jeton = jwtService.genererAccessToken(UUID.randomUUID(), null, Set.of());

        UtilisateurPrincipal principal = jwtService.analyser(jeton);

        assertThat(principal.etablissementId()).isNull();
        assertThat(principal.codesRoles()).isEmpty();
    }

    @Test
    void estValideRenvoieTrue_quandJetonValide() {
        String jeton = jwtService.genererAccessToken(UUID.randomUUID(), null, Set.of());

        assertThat(jwtService.estValide(jeton)).isTrue();
    }

    @Test
    void refuseLeJeton_quandExpire() {
        String jetonExpire = jetonSigneAvec(SECRET_32_OCTETS_MINIMUM, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> jwtService.analyser(jetonExpire)).isInstanceOf(JwtException.class);
        assertThat(jwtService.estValide(jetonExpire)).isFalse();
    }

    @Test
    void refuseLeJeton_quandSigneAvecUneAutreCle() {
        String jetonAutreCle = jetonSigneAvec(
                AUTRE_SECRET_32_OCTETS_MINIMUM, Instant.now(), Instant.now().plusSeconds(900));

        assertThatThrownBy(() -> jwtService.analyser(jetonAutreCle)).isInstanceOf(JwtException.class);
        assertThat(jwtService.estValide(jetonAutreCle)).isFalse();
    }

    @Test
    void refuseLeJeton_quandTronqueOuMalforme() {
        String jetonValide = jwtService.genererAccessToken(UUID.randomUUID(), null, Set.of());
        String jetonTronque = jetonValide.substring(0, jetonValide.length() - 10);

        assertThatThrownBy(() -> jwtService.analyser(jetonTronque)).isInstanceOf(JwtException.class);
        assertThat(jwtService.estValide(jetonTronque)).isFalse();
        assertThat(jwtService.estValide("ceci-n-est-pas-un-jwt")).isFalse();
    }

    @Test
    void refuseLaConstruction_quandSecretAbsent() {
        assertThatThrownBy(() -> new JwtService(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refuseLaConstruction_quandSecretTropCourt() {
        assertThatThrownBy(() -> new JwtService("trop-court")).isInstanceOf(IllegalStateException.class);
    }

    private String jetonSigneAvec(String secret, Instant emissionA, Instant expirationA) {
        SecretKey cle = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("roles", Set.of())
                .issuedAt(Date.from(emissionA))
                .expiration(Date.from(expirationA))
                .signWith(cle)
                .compact();
    }
}
