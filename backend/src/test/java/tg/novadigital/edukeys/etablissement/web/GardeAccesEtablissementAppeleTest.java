package tg.novadigital.edukeys.etablissement.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test statique (durcissement post-revue T-10) : le bloquant sur {@code GET
 * /api/v1/etablissements} venait d'un oubli d'appel manuel à
 * {@link GardeAccesEtablissement} — {@code Etablissement} échappe au filtre
 * Hibernate multi-établissement (ADR-0002), donc rien d'automatique ne borne
 * un ADMIN à son propre établissement sur un endpoint identifié par
 * {@code id}/{@code etablissementId}.
 *
 * <p>Analyse texte (même esprit qu'{@code AucuneDesactivationDuFiltreTest})
 * plutôt qu'ArchUnit bytecode : la règle porte sur un appel de méthode dans
 * le <em>corps</em> d'une méthode publique de contrôleur précise, ce
 * qu'ArchUnit exprime mal (il raisonne sur les types et les dépendances entre
 * classes, pas sur « telle méthode appelle telle autre depuis son corps » de
 * façon lisible). Chaque méthode de contrôleur de ce module est précédée
 * d'une annotation {@code @Operation} (convention constante observée dans
 * les trois contrôleurs) : découper le fichier sur cette annotation isole
 * proprement le corps de chaque endpoint.</p>
 *
 * <p><b>Liste d'exemption explicite</b>, documentée ici et nulle part
 * ailleurs : {@code EtablissementController.creer} (POST, crée
 * l'établissement — aucun {@code id} à garder avant sa création) et
 * {@code EtablissementController.obtenirCourant} (endpoint {@code /courant},
 * aucun {@code {id}} dans le chemin : l'établissement résolu vient du
 * contexte multi-établissement de l'appelant, jamais d'un identifiant fourni
 * par la requête), et {@code EtablissementController.desactiver}/
 * {@code .reactiver} : opérations plateforme gardées par
 * {@code ETABLISSEMENT_CREER}, qu'aucun ADMIN ne porte (ADR-0002) — seul
 * SUPER_ADMIN les atteint, et agit délibérément sur un établissement qui
 * n'est pas le sien.</p>
 */
class GardeAccesEtablissementAppeleTest {

    /** "FichierController#methode" : les seuls endpoints dispensés de l'appel, avec leur justification en Javadoc de classe. */
    private static final Set<String> METHODES_EXEMPTEES = Set.of(
            "EtablissementController#creer",
            "EtablissementController#obtenirCourant",
            "EtablissementController#desactiver",
            "EtablissementController#reactiver");

    private static final Pattern SIGNATURE_METHODE_PUBLIQUE = Pattern.compile(
            "public\\s+[\\w<>\\[\\],. ?]+\\s+(\\w+)\\s*\\(([^)]*)\\)");

    private static final Pattern PARAMETRE_ID_ETABLISSEMENT = Pattern.compile(
            "@PathVariable\\s+UUID\\s+(id|etablissementId)\\b");

    private static final Pattern APPEL_GARDE = Pattern.compile("GardeAccesEtablissement\\.verifierAcces\\s*\\(");

    @Test
    @DisplayName("toute methode publique de controleur prenant un id/etablissementId (UUID) appelle GardeAccesEtablissement, sauf exemption documentee")
    void toutEndpointAvecIdAppelleLeGarde() throws IOException {
        List<String> violations = new ArrayList<>();
        Path racine = racineSourcesWeb();

        List<Path> fichiersControleurs;
        try (var flux = Files.list(racine)) {
            fichiersControleurs = flux
                    .filter(chemin -> chemin.getFileName().toString().endsWith("Controller.java"))
                    .sorted()
                    .toList();
        }
        if (fichiersControleurs.isEmpty()) {
            throw new IllegalStateException(
                    "ECHEC DE RESOLUTION DES SOURCES (pas une violation de la regle testee) : aucun *Controller.java "
                            + "trouve dans " + racine.toAbsolutePath() + ". Corrigez racineSourcesWeb() avant de faire confiance a ce test.");
        }

        for (Path fichier : fichiersControleurs) {
            String nomFichier = fichier.getFileName().toString();
            String contenu = Files.readString(fichier);
            String nomClasse = nomFichier.substring(0, nomFichier.length() - ".java".length());

            for (String bloc : contenu.split("(?=@Operation)")) {
                Matcher signature = SIGNATURE_METHODE_PUBLIQUE.matcher(bloc);
                if (!signature.find()) {
                    continue;
                }
                String nomMethode = signature.group(1);
                String parametres = signature.group(2);
                String identifiant = nomClasse + "#" + nomMethode;

                if (!PARAMETRE_ID_ETABLISSEMENT.matcher(parametres).find()) {
                    continue;
                }
                if (METHODES_EXEMPTEES.contains(identifiant)) {
                    continue;
                }
                if (!APPEL_GARDE.matcher(bloc).find()) {
                    violations.add(identifiant);
                }
            }
        }

        assertThat(violations)
                .withFailMessage(() -> "Methode(s) de controleur prenant un id/etablissementId sans appel a "
                        + "GardeAccesEtablissement.verifierAcces(...) : " + violations + ". Sans ce garde, un ADMIN "
                        + "authentifie peut acceder aux donnees d'un etablissement qui n'est pas le sien (R11). "
                        + "Ajoutez l'appel, ou si l'endpoint est legitimement exempte (creation sans id, endpoint "
                        + "sans {id} dans le chemin), ajoutez-le a "
                        + "GardeAccesEtablissementAppeleTest.METHODES_EXEMPTEES en le justifiant.")
                .isEmpty();
    }

    /**
     * {@code src/main/java/.../etablissement/web}, relatif au répertoire de
     * travail du module (Surefire l'exécute depuis {@code backend/}) — avec
     * repli explicite si jamais ce test était lancé depuis un autre
     * répertoire de travail.
     */
    private static Path racineSourcesWeb() {
        Path relatif = Paths.get("src", "main", "java", "tg", "novadigital", "edukeys", "etablissement", "web");
        if (Files.isDirectory(relatif)) {
            return relatif;
        }
        Path depuisRacineDepot = Paths.get(
                "backend", "src", "main", "java", "tg", "novadigital", "edukeys", "etablissement", "web");
        if (Files.isDirectory(depuisRacineDepot)) {
            return depuisRacineDepot;
        }
        throw new UncheckedIOException(new IOException(
                "Repertoire des sources du module etablissement.web introuvable (ni " + relatif.toAbsolutePath()
                        + " ni " + depuisRacineDepot.toAbsolutePath() + ")"));
    }
}
