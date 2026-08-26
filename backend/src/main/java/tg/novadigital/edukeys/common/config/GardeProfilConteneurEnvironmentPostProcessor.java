package tg.novadigital.edukeys.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuse de démarrer si un profil de développement ({@code local} ou
 * {@code test} — tous deux chargent {@code db/testdata}, comptes de
 * démonstration aux mots de passe connus) est actif à l'intérieur d'un
 * conteneur Docker (revue post-T-05, point CI n°67).
 *
 * <p><b>Pourquoi un {@link EnvironmentPostProcessor}</b> plutôt qu'un
 * {@code ApplicationRunner} comme {@link VerificateurProfilActif} : il
 * s'exécute avant même la création du {@code ApplicationContext}, donc avant
 * Flyway et toute connexion à une base de données. Un {@code ApplicationRunner}
 * n'agirait qu'après un rafraîchissement de contexte réussi — c'est-à-dire
 * après que {@code db/testdata} ait déjà été appliqué à une base de données
 * réellement accessible. Le scénario que ce composant empêche n'est pas
 * « le conteneur démarre avec le mauvais profil » (gênant, mais rattrapable
 * avant que quiconque n'y accède), mais « le profil {@code local} pointe, par
 * une variable d'environnement mal positionnée, vers une base de production
 * réelle et y sème des comptes de démonstration » — irréversible si Flyway a
 * déjà tourné.</p>
 *
 * <p>Détecté via {@code /.dockerenv}, posé par le runtime Docker dans tout
 * conteneur, qu'il tourne en CI ({@code docker run} de vérification) ou sur
 * Render (déploiement par image, {@code render.yaml}) — jamais présent sur un
 * poste de développement classique, où {@code local}/{@code test} sont
 * légitimes.</p>
 *
 * <p>Enregistré via {@code META-INF/spring.factories} (clé
 * {@code org.springframework.boot.env.EnvironmentPostProcessor}) :
 * contrairement à {@code AutoConfiguration}, cette interface n'est pas
 * éligible au mécanisme plus récent {@code *.imports} —
 * {@code SpringFactoriesLoader} continue de la découvrir via l'ancien
 * fichier. Une classe {@code EnvironmentPostProcessor} est instanciée avant
 * qu'aucun bean Spring n'existe, donc sans injection de dépendances ni logger
 * Spring pleinement configuré — l'exception elle-même, relayée par le
 * rapporteur d'échec de démarrage de Spring Boot, est le seul canal de
 * visibilité disponible à ce stade, mais elle reste très visible : elle
 * interrompt {@code SpringApplication.run(...)} avant tout autre
 * traitement.</p>
 */
public class GardeProfilConteneurEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Set<String> PROFILS_DEVELOPPEMENT = Set.of("local", "test");

    private static final Path MARQUEUR_CONTENEUR = Path.of("/.dockerenv");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!enConteneur()) {
            return;
        }
        for (String profil : environment.getActiveProfiles()) {
            if (PROFILS_DEVELOPPEMENT.contains(profil)) {
                throw new IllegalStateException(
                        "Profil '" + profil + "' actif dans un conteneur Docker : ce profil charge db/testdata "
                                + "(comptes de démonstration, mots de passe connus) et n'est légitime que sur un "
                                + "poste de développement. Vérifiez SPRING_PROFILES_ACTIVE pour cet environnement.");
            }
        }
    }

    private static boolean enConteneur() {
        return Files.exists(MARQUEUR_CONTENEUR);
    }
}
