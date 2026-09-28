package tg.novadigital.edukeys.admission.web;

import tg.novadigital.edukeys.admission.AdmissionProperties;
import tg.novadigital.edukeys.admission.service.VerificationTurnstileService;

/**
 * Double de test de {@link VerificationTurnstileService} (US-06) : remplace
 * l'appel HTTP réel sans introduire de serveur simulé (ni WireMock ni
 * MockWebServer, proscrits par le projet). Piloté par {@link #definirMode},
 * réinitialisé à {@link Mode#SUCCES} entre chaque test — jamais de valeur par
 * défaut permissive oubliée d'un test à l'autre. La vérification anti-robot
 * reste donc active et refusante par défaut dans le reste de l'application
 * (production comme profil {@code local}) : seul ce bean, remplacé par
 * {@code @Primary} dans les tests qui l'importent explicitement, change de
 * comportement.
 */
public class VerificationTurnstileServiceDouble extends VerificationTurnstileService {

    public enum Mode {
        SUCCES,
        ECHEC,
        /** Simule une indisponibilité réseau du service Turnstile : refus, comme le vrai service (règle 4). */
        SERVICE_INDISPONIBLE
    }

    private static volatile Mode mode = Mode.SUCCES;

    public VerificationTurnstileServiceDouble(AdmissionProperties proprietes) {
        super(proprietes);
    }

    public static void definirMode(Mode nouveauMode) {
        mode = nouveauMode;
    }

    public static void reinitialiser() {
        mode = Mode.SUCCES;
    }

    @Override
    public boolean verifier(String jeton, String adresseIp) {
        if (jeton == null || jeton.isBlank()) {
            return false;
        }
        return switch (mode) {
            case SUCCES -> true;
            case ECHEC, SERVICE_INDISPONIBLE -> false;
        };
    }
}
