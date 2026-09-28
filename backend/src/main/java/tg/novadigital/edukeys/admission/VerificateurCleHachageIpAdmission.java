package tg.novadigital.edukeys.admission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fait échouer le démarrage plutôt qu'une clé HMAC par défaut silencieusement
 * active (I7, revue US-06) — même exigence que {@code VerificateurProfilActif}
 * pour {@code SPRING_PROFILES_ACTIVE} : {@code edukeys.admission.sel-hachage-ip}
 * ("changez-moi" par défaut) sert de clé secrète HMAC-SHA256 pour hacher les
 * adresses IP soumises (règle 4 de la spec US-06). Une valeur par défaut
 * connue de tous les dépôts publics équivaut à l'absence de clé.
 */
@Component
public class VerificateurCleHachageIpAdmission implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificateurCleHachageIpAdmission.class);
    private static final String VALEUR_PAR_DEFAUT = "changez-moi";

    private final AdmissionProperties proprietes;
    private final Environment environment;

    public VerificateurCleHachageIpAdmission(AdmissionProperties proprietes, Environment environment) {
        this.proprietes = proprietes;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            return;
        }
        String cle = proprietes.getSelHachageIp();
        if (cle == null || cle.isBlank() || VALEUR_PAR_DEFAUT.equals(cle)) {
            log.error("configuration_invalide motif=cle_hachage_ip_admission_absente_ou_par_defaut");
            throw new IllegalStateException(
                    "edukeys.admission.sel-hachage-ip (EDUKEYS_ADMISSION_SEL_HACHAGE_IP) doit être défini explicitement "
                            + "hors des profils local/test, avec une valeur différente du défaut de développement.");
        }
    }
}
