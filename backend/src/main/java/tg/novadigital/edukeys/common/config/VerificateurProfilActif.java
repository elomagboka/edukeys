package tg.novadigital.edukeys.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fait échouer le démarrage plutôt qu'un profil silencieusement absent
 * (revue post-T-05, points CI n°66/67) — même exigence que
 * {@code edukeys.securite.jwt.secret} (aucune valeur de repli sur
 * {@code ${EDUKEYS_JWT_SECRET}}) : un défaut implicite qui semble pratique en
 * développement devient une fuite en production dès qu'il survit au premier
 * environnement où personne n'a pensé à le surcharger explicitement.
 *
 * <p>{@code application.yml} ne fixe plus {@code spring.profiles.active: local}
 * (ce défaut faisait démarrer silencieusement n'importe quel conteneur — y
 * compris en production, si {@code SPRING_PROFILES_ACTIVE} était oublié au
 * déploiement — avec le profil {@code local}, qui charge {@code db/testdata} :
 * comptes de démonstration aux mots de passe connus).
 * {@code SPRING_PROFILES_ACTIVE} doit désormais être positionné explicitement
 * dans chaque environnement (voir {@code render.yaml} pour recette/production,
 * {@code -Dspring-boot.run.profiles=local} en local, CLAUDE.md).</p>
 *
 * <p><b>Pourquoi un {@code ApplicationRunner} et pas plus tôt</b> : cette
 * classe ne couvre que l'absence totale de profil, un cas où aucune source de
 * données n'est de toute façon configurée ({@code spring.datasource.url}
 * n'existe que dans les fichiers spécifiques à un profil) — l'échec réel se
 * produit donc déjà pendant le rafraîchissement du contexte (Flyway/DataSource),
 * avant que cet {@code ApplicationRunner} ne s'exécute. Ce composant reste une
 * seconde ligne de défense explicite, au message plus clair, pour le jour où
 * une configuration de source de données cesserait d'être conditionnée à un
 * profil. Le cas réellement dangereux — profil {@code local}/{@code test} actif
 * dans un conteneur, qui chargerait {@code db/testdata} dans une vraie base
 * avant qu'aucun {@code ApplicationRunner} ne puisse intervenir — est couvert
 * bien plus tôt par {@link GardeProfilConteneurEnvironmentPostProcessor},
 * qui s'exécute avant même la création du contexte Spring.</p>
 */
@Component
public class VerificateurProfilActif implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificateurProfilActif.class);

    private final Environment environment;

    public VerificateurProfilActif(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.getActiveProfiles().length == 0) {
            String message = "Aucun profil Spring actif : SPRING_PROFILES_ACTIVE doit être défini explicitement "
                    + "(local, test, recette ou production) — aucune valeur de repli, même exigence que "
                    + "EDUKEYS_JWT_SECRET. Un défaut implicite sur 'local' chargerait silencieusement les comptes "
                    + "de démonstration de db/testdata dans n'importe quel environnement.";
            log.error("configuration_invalide motif=aucun_profil_actif");
            throw new IllegalStateException(message);
        }
    }
}
