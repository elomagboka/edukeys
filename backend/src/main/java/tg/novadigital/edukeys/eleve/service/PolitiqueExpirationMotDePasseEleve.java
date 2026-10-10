package tg.novadigital.edukeys.eleve.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Date d'expiration du premier mot de passe d'un compte élève (US-08) :
 * {@code max(maintenant + 14 j, début de l'année scolaire (début de journée, fuseau de l'établissement) + 30 j)}.
 *
 * <p>Aucun plafonnement ici : une inscription du 15 mai pour une rentrée du 1er septembre expire le
 * 1er octobre (139 j), pas avant la rentrée. Le maximum accepté est celui du port d'émission
 * ({@code edukeys.securite.mot-de-passe-temporaire.expiration-max}) ; au-delà il refuse en 422
 * ({@code MOT_DE_PASSE_TEMPORAIRE_EXPIRATION_HORS_BORNES}), sans jamais ramener la date en silence.</p>
 */
@Component
public class PolitiqueExpirationMotDePasseEleve {

    private final long delaiMinimalJours;
    private final long delaiApresRentreeJours;

    public PolitiqueExpirationMotDePasseEleve(
            @Value("${edukeys.eleve.mot-de-passe-temporaire.delai-minimal-jours:14}") long delaiMinimalJours,
            @Value("${edukeys.eleve.mot-de-passe-temporaire.delai-apres-rentree-jours:30}") long delaiApresRentreeJours) {
        this.delaiMinimalJours = delaiMinimalJours;
        this.delaiApresRentreeJours = delaiApresRentreeJours;
    }

    public Instant calculer(Instant maintenant, LocalDate debutAnnee, ZoneId fuseau) {
        Instant auMoinsDansLeDelai = maintenant.plus(delaiMinimalJours, ChronoUnit.DAYS);
        Instant apresLaRentree = debutAnnee.atStartOfDay(fuseau).plusDays(delaiApresRentreeJours).toInstant();
        return auMoinsDansLeDelai.isAfter(apresLaRentree) ? auMoinsDansLeDelai : apresLaRentree;
    }
}
