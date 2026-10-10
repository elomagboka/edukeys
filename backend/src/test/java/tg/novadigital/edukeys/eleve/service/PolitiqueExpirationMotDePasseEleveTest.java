package tg.novadigital.edukeys.eleve.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class PolitiqueExpirationMotDePasseEleveTest {

    private static final ZoneId LOME = ZoneId.of("Africa/Lome");
    private final PolitiqueExpirationMotDePasseEleve politique = new PolitiqueExpirationMotDePasseEleve(14, 30);

    @Test
    void inscriptionAnticipee_expireUnMoisApresLaRentree_jamaisAvant() {
        // 15 mai, rentrée le 1er septembre : le 1er octobre (139 j), pas le 29 mai ni le 13 août (ancien plafond silencieux).
        Instant inscription = Instant.parse("2026-05-15T10:00:00Z");

        Instant expiration = politique.calculer(inscription, LocalDate.of(2026, 9, 1), LOME);

        assertThat(expiration).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void inscriptionTardive_apresLaRentree_expireQuatorzeJoursApres() {
        Instant inscription = Instant.parse("2026-11-20T08:30:00Z");

        Instant expiration = politique.calculer(inscription, LocalDate.of(2026, 9, 1), LOME);

        assertThat(expiration).isEqualTo(Instant.parse("2026-12-04T08:30:00Z"));
    }

    @Test
    void debutDeJourneeSeLitDansLeFuseauDeLEtablissement() {
        // Début de journée à UTC+3 : minuit local = 21 h UTC la veille.
        Instant inscription = Instant.parse("2026-05-15T10:00:00Z");

        Instant expiration = politique.calculer(inscription, LocalDate.of(2026, 9, 1), ZoneId.of("Europe/Moscow"));

        assertThat(expiration).isEqualTo(Instant.parse("2026-09-30T21:00:00Z"));
    }
}
