package tg.novadigital.edukeys.identite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.common.exception.AccesInterditException;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.identite.EmetteurMotDePasseTemporaire;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * GARDE-FOU (exigence du PO) : aucune méthode qui émet, régénère ou pose un
 * secret (mot de passe, jeton) ne doit pouvoir être appelée sur un compte
 * hors de l'établissement courant. C'est la faille qui a été corrigée trois
 * fois en US-04 (rattachement, régénération, puis émission à expiration
 * explicite d'US-08a) ; ce test empêche qu'une « quatrième porte » s'ouvre.
 *
 * <p><strong>Deux découvertes complémentaires</strong>, par ArchUnit sur le bytecode, jamais par une liste écrite à la main :</p>
 * <ol>
 *   <li><em>Motif de nom</em> : toute méthode publique d'une classe ou interface du module {@code identite} (racine,
 *       {@code service}, {@code security}) dont le nom qualifié {@code Classe.méthode} correspond à {@value #MOTIF}
 *       (insensible à la casse) ;</li>
 *   <li><em>Graphe d'appels</em> : toute méthode publique de ces mêmes classes qui <strong>atteint transitivement</strong>
 *       un « puits de secret » ({@link #estPuits} : {@code Utilisateur.changerMotDePasseHache}, constructeur
 *       d'{@code Utilisateur}, drapeau de changement obligatoire, constructeurs de {@code JetonActivationCompte} /
 *       {@code JetonRafraichissement}, {@code GenerateurMotDePasseTemporaire.generer},
 *       {@code JetonHacheur.genererJetonEnClair}, {@code PasswordEncoder.encode}), y compris à travers un port
 *       (interface) et ses implémentations. Cela attrape une méthode dont le nom ne contient ni « motDePasse » ni
 *       « jeton » (ex. {@code basculerEtablissement}, {@code creerCompteEleve} d'US-08).</li>
 * </ol>
 *
 * <p><strong>Exhaustivité</strong> : chaque méthode découverte doit figurer soit dans {@link #bornees} (appelée sur un
 * compte d'un autre établissement, un SUPER_ADMIN, un compte affecté ailleurs, un compte inactif, un identifiant
 * inconnu, un compte du personnel cumulant PARENT — refus exigé, aucune écriture), soit dans {@link #EXCLUES} avec une
 * justification écrite (UUID issu du principal authentifié, borné par la possession d'un secret, crée un compte neuf
 * — et alors un test le prouve). Une méthode nouvellement ajoutée qui atteint un puits sans être déclarée fait échouer
 * {@link #toutesLesMethodesSurveilleesSontCouvertes} : le développeur doit la borner et l'ajouter à la table, ou
 * justifier l'exclusion en revue. Périmètre : {@code identite}, {@code identite.service}, {@code identite.security}
 * (les contrôleurs ne contiennent aucune logique et délèguent à ces classes).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class GardeBornesSecretsIntegrationTest {

    static final String MOTIF = "(?i).*(motdepasse|jeton).*";
    private static final Pattern MOTIF_COMPILE = Pattern.compile(MOTIF);

    private static final UUID ETABLISSEMENT_A = UUID.fromString("01977000-0000-7000-9000-000000000001");
    private static final UUID ETABLISSEMENT_B = UUID.fromString("01977000-0000-7000-9000-000000000002");

    /** Invocation d'une méthode surveillée sur un compte cible, dans le contexte de l'établissement A. */
    @FunctionalInterface
    interface Invocation {
        void appeler(UUID utilisateurCibleId) throws Exception;
    }

    /** @param refuseUnCompteDuPersonnel vrai si la méthode est réservée aux comptes ELEVE/PARENT (refuse un compte ENSEIGNANT de A). */
    record CasBorne(Invocation invocation, boolean refuseUnCompteDuPersonnel) {
    }

    @Autowired
    private UtilisateurService utilisateurService;
    @Autowired
    private EmetteurMotDePasseTemporaire emetteur;
    @Autowired
    private EmetteurMotDePasseTemporaireImpl emetteurImpl;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    /** Méthodes bornées à l'établissement courant : clé = {@code Classe.méthode}. */
    private Map<String, CasBorne> bornees() {
        Map<String, CasBorne> table = new LinkedHashMap<>();
        table.put("UtilisateurService.regenererMotDePasseTemporaire",
                new CasBorne(id -> utilisateurService.regenererMotDePasseTemporaire(id), false));
        table.put("UtilisateurService.emettreMotDePasseTemporaireAvecExpiration",
                new CasBorne(id -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(id, expirationValide()), true));
        table.put("EmetteurMotDePasseTemporaireImpl.emettreAvecExpiration",
                new CasBorne(id -> emetteurImpl.emettreAvecExpiration(id, expirationValide()), true));
        table.put("EmetteurMotDePasseTemporaire.emettreAvecExpiration",
                new CasBorne(id -> emetteur.emettreAvecExpiration(id, expirationValide()), true));
        return table;
    }

    /**
     * Méthodes qui atteignent un puits de secret ou correspondent au motif, volontairement NON bornées à
     * l'établissement courant : clé = {@code Classe.méthode}, valeur = justification écrite (revue obligatoire).
     */
    private static final Map<String, String> EXCLUES = Map.ofEntries(
            Map.entry("UtilisateurService.changerMotDePasseSoiMeme",
                    "Libre-service : l'identifiant est celui du principal authentifié (jamais saisi par le client) et l'ancien mot de passe est exigé."),
            Map.entry("UtilisateurService.creerCompteAvecRoles",
                    "Crée un compte NEUF : refuse (409) tout identifiant/email déjà porté par un compte actif, ne peut jamais viser un compte existant "
                            + "(prouvé par lesMethodesDeCreationNeVisentJamaisUnCompteExistant)."),
            Map.entry("UtilisateurService.creerCompteAdministrateurInitial",
                    "Crée un compte NEUF (délègue à creerCompteAvecRoles) : ne peut viser un compte existant "
                            + "(prouvé par lesMethodesDeCreationNeVisentJamaisUnCompteExistant)."),
            Map.entry("CreateurCompteAdministrateur.creerAdministrateur",
                    "Port de création de compte NEUF (délègue à creerCompteAdministrateurInitial) : prouvé par lesMethodesDeCreationNeVisentJamaisUnCompteExistant."),
            Map.entry("CreateurCompteAdministrateurImpl.creerAdministrateur",
                    "Crée un compte NEUF (délègue à creerCompteAdministrateurInitial) : prouvé par lesMethodesDeCreationNeVisentJamaisUnCompteExistant."),
            Map.entry("UtilisateurService.creerCompteEleve",
                    "Crée un compte NEUF (identifiant = matricule) : refuse (409) tout identifiant déjà porté par un compte actif, ne peut jamais "
                            + "viser un compte existant (prouvé par lesMethodesDeCreationNeVisentJamaisUnCompteExistant)."),
            Map.entry("CreateurCompteEleve.creerCompteEleve",
                    "Port de création de compte NEUF (délègue à UtilisateurService.creerCompteEleve) : prouvé par lesMethodesDeCreationNeVisentJamaisUnCompteExistant."),
            Map.entry("CreateurCompteEleveImpl.creerCompteEleve",
                    "Crée un compte NEUF (délègue à UtilisateurService.creerCompteEleve) : prouvé par lesMethodesDeCreationNeVisentJamaisUnCompteExistant."),
            Map.entry("AuthService.connecter",
                    "Borné par la possession du mot de passe (BCrypt) du compte visé ; aucun UUID fourni, les jetons sont émis pour le compte authentifié lui-même."),
            Map.entry("AuthService.rafraichir",
                    "Borné par la possession du jeton de rafraîchissement opaque (haché en base) ; un rejeu révoque toute la famille de jetons."),
            Map.entry("AuthService.basculerEtablissement",
                    "UUID issu du principal authentifié (AuthController), jamais d'un paramètre client — verrouillé par "
                            + "seuleLAuthControllerAppelleBasculerEtablissement ; l'affectation à l'établissement cible est vérifiée."),
            Map.entry("JetonHacheur.hacher", "Fonction pure (SHA-256) : n'écrit rien."),
            Map.entry("JetonHacheur.genererJetonEnClair", "Fonction pure (aléa) : n'écrit rien, ne lit aucun compte."),
            Map.entry("GenerateurMotDePasseTemporaire.generer", "Fonction pure (aléa) : n'écrit rien, ne lit aucun compte."));

    private static Instant expirationValide() {
        return Instant.now().plus(Duration.ofDays(5));
    }

    // ------------------------------------------------------------------
    // 1. Exhaustivité, par découverte ArchUnit (motif de nom + graphe d'appels)
    // ------------------------------------------------------------------

    private static final String PAQUET_IDENTITE = "tg.novadigital.edukeys.identite";

    /**
     * Puits de secret : toute écriture ou génération de mot de passe / jeton du module. Liste vérifiée contre le code
     * (grep des appels dans {@code identite}) : hash de mot de passe, drapeau de changement obligatoire, jetons
     * d'activation et de rafraîchissement, générateurs d'aléa, et {@code PasswordEncoder.encode}. Ajouter ici tout
     * nouveau point d'écriture d'un secret.
     */
    private static boolean estPuits(JavaClass proprietaireClasse, String membre) {
        // Toute implémentation de PasswordEncoder (BCrypt, délégant...), pas seulement l'interface.
        if (proprietaireClasse.isAssignableTo(PasswordEncoder.class)) {
            return membre.equals("encode");
        }
        return switch (proprietaireClasse.getName()) {
            case PAQUET_IDENTITE + ".domain.Utilisateur" -> membre.equals("<init>")
                    || membre.equals("changerMotDePasseHache")
                    || membre.equals("exigerChangementMotDePasse")
                    || membre.equals("confirmerChangementMotDePasse");
            case PAQUET_IDENTITE + ".domain.JetonActivationCompte" -> membre.equals("<init>");
            case PAQUET_IDENTITE + ".domain.JetonRafraichissement" -> membre.equals("<init>");
            case PAQUET_IDENTITE + ".service.GenerateurMotDePasseTemporaire" -> membre.equals("generer");
            case PAQUET_IDENTITE + ".service.JetonHacheur" -> membre.equals("genererJetonEnClair");
            default -> false;
        };
    }

    /** Implémentations (dans le module) d'une méthode d'interface : même nom, même nombre de paramètres. */
    private static List<JavaMethod> implementations(JavaMethod methodeAbstraite) {
        List<JavaMethod> resultat = new ArrayList<>();
        for (JavaClass sousType : methodeAbstraite.getOwner().getAllSubclasses()) {
            for (JavaMethod candidate : sousType.getMethods()) {
                if (candidate.getName().equals(methodeAbstraite.getName())
                        && candidate.getRawParameterTypes().size() == methodeAbstraite.getRawParameterTypes().size()
                        && !candidate.getModifiers().contains(JavaModifier.ABSTRACT)) {
                    resultat.add(candidate);
                }
            }
        }
        return resultat;
    }

    /** Vrai si l'unité de code atteint (transitivement, à l'intérieur du module) un puits de secret. */
    private static boolean atteintUnPuits(JavaCodeUnit unite, Set<JavaCodeUnit> visites) {
        if (!visites.add(unite)) {
            return false;
        }
        if (unite instanceof JavaMethod methode && methode.getOwner().isInterface()
                && methode.getModifiers().contains(JavaModifier.ABSTRACT)) {
            // Appel par interface (port) : le code exécuté est celui des implémentations.
            return implementations(methode).stream().anyMatch(impl -> atteintUnPuits(impl, visites));
        }
        List<JavaCodeUnit> cibles = new ArrayList<>();
        // getAccessesFromSelf : appels ET références de méthode/constructeur (passwordEncoder::encode,
        // cible::changerMotDePasseHache, JetonActivationCompte::new), que getMethodCallsFromSelf ne voit pas.
        for (JavaAccess<?> acces : unite.getAccessesFromSelf()) {
            if (!(acces instanceof JavaCodeUnitAccess<?> accesCode)) {
                continue; // accès à un champ : pas un appel de code
            }
            if (estPuits(accesCode.getTargetOwner(), accesCode.getTarget().getName())) {
                return true;
            }
            accesCode.getTarget().resolveMember().ifPresent(cibles::add);
        }
        for (JavaCodeUnit cible : cibles) {
            if (cible.getOwner().getPackageName().startsWith(PAQUET_IDENTITE) && atteintUnPuits(cible, visites)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Méthodes publiques du module (racine, service, security) qui correspondent au motif de nom OU atteignent un
     * puits de secret par le graphe d'appels. Limite connue : ArchUnit ne relie pas une méthode au corps de ses
     * lambdas (invokedynamic) ; par prudence, une lambda qui atteint un puits est attribuée à TOUTES les méthodes
     * publiques de sa classe (sur-approximation : au pire une entrée à justifier en trop, jamais un trou).
     */
    static Set<String> decouvrirMethodesSurveillees() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(PAQUET_IDENTITE);

        // Garde contre un import silencieusement vide (ArchUnit rendu inopérant par une version de Java trop récente,
        // voir CLAUDE.md) : sans ces classes connues, la découverte ne prouverait plus rien.
        assertThat(classes.contain(UtilisateurService.class)).as("ArchUnit importe UtilisateurService").isTrue();
        assertThat(classes.contain(EmetteurMotDePasseTemporaire.class)).as("ArchUnit importe le port racine").isTrue();
        assertThat(classes.size()).as("nombre de classes importées par ArchUnit").isGreaterThan(30);
        // Garde du graphe d'appels : sans appels résolus, le parcours ne prouverait rien non plus.
        assertThat(classes.get(UtilisateurService.class).getMethods().stream()
                .anyMatch(m -> m.getName().equals("regenererMotDePasseTemporaire") && atteintUnPuits(m, new HashSet<>())))
                .as("le graphe d'appels atteint un puits depuis regenererMotDePasseTemporaire").isTrue();

        Set<String> trouvees = new TreeSet<>();
        for (JavaClass classe : classes) {
            String paquet = classe.getPackageName();
            boolean dansPerimetre = paquet.equals(PAQUET_IDENTITE)
                    || paquet.equals(PAQUET_IDENTITE + ".service")
                    || paquet.equals(PAQUET_IDENTITE + ".security");
            if (!dansPerimetre || classe.isRecord() || classe.isAnonymousClass()) {
                continue;
            }
            boolean lambdaAtteintUnPuits = classe.getMethods().stream()
                    .anyMatch(m -> m.getName().startsWith("lambda$") && atteintUnPuits(m, new HashSet<>()));
            for (JavaMethod methode : classe.getMethods()) {
                boolean publique = methode.getModifiers().contains(JavaModifier.PUBLIC);
                boolean synthetique = methode.getModifiers().contains(JavaModifier.SYNTHETIC) || methode.getName().startsWith("lambda$");
                if (!publique || synthetique) {
                    continue;
                }
                String cle = classe.getSimpleName() + "." + methode.getName();
                if (MOTIF_COMPILE.matcher(cle).matches() || lambdaAtteintUnPuits || atteintUnPuits(methode, new HashSet<>())) {
                    trouvees.add(cle);
                }
            }
        }
        return trouvees;
    }

    @Test
    void toutesLesMethodesSurveilleesSontCouvertes() {
        Set<String> decouvertes = decouvrirMethodesSurveillees();
        Set<String> declarees = new TreeSet<>(bornees().keySet());
        declarees.addAll(EXCLUES.keySet());

        Set<String> nonCouvertes = new TreeSet<>(decouvertes);
        nonCouvertes.removeAll(declarees);
        assertThat(nonCouvertes)
                .as("Méthodes du module identite correspondant à " + MOTIF + " OU atteignant un puits de secret (graphe d'appels) "
                        + "sans borne testée : les ajouter à bornees() (après les avoir bornées à l'établissement courant) "
                        + "ou à EXCLUES avec justification écrite")
                .isEmpty();

        Set<String> obsoletes = new TreeSet<>(declarees);
        obsoletes.removeAll(decouvertes);
        assertThat(obsoletes).as("Entrées de la table qui ne correspondent plus à aucune méthode").isEmpty();
    }

    @Test
    void seuleLAuthControllerAppelleBasculerEtablissement() {
        // Justification de l'exclusion de AuthService.basculerEtablissement : l'UUID reçu ne vient jamais d'un client,
        // mais du principal authentifié. Aucun autre appelant ne doit pouvoir lui passer un UUID arbitraire.
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("tg.novadigital.edukeys");
        var methode = classes.get(AuthService.class).getMethod("basculerEtablissement", UUID.class, UUID.class);
        // getAccessesToSelf : appels ET références de méthode (AuthService::basculerEtablissement).
        assertThat(methode.getAccessesToSelf()).as("accès à basculerEtablissement").isNotEmpty();
        assertThat(methode.getAccessesToSelf())
                .allSatisfy(appel -> assertThat(appel.getOriginOwner().getSimpleName()).isEqualTo("AuthController"));
    }

    private static JavaClasses classesIdentite() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(PAQUET_IDENTITE);
    }

    /**
     * Angle mort (a) : la découverte ne couvre pas {@code identite.web}. Cette règle justifie cette exclusion :
     * aucun contrôleur (ni aucune classe de {@code identite.web}) n'accède directement à un puits de secret ni à un
     * repository — il passe par un service, déjà couvert.
     */
    @Test
    void identiteWeb_n_accedeAAucunPuitsDeSecret_nAUnRepository() {
        List<String> violations = new ArrayList<>();
        for (JavaClass classe : classesIdentite()) {
            if (!classe.getPackageName().equals(PAQUET_IDENTITE + ".web")) {
                continue;
            }
            for (JavaCodeUnit unite : classe.getCodeUnits()) {
                for (JavaAccess<?> acces : unite.getAccessesFromSelf()) {
                    if (acces instanceof JavaCodeUnitAccess<?> accesCode
                            && (estPuits(accesCode.getTargetOwner(), accesCode.getTarget().getName())
                                    || accesCode.getTargetOwner().getPackageName().equals(PAQUET_IDENTITE + ".repository"))) {
                        violations.add(classe.getSimpleName() + "." + unite.getName() + " -> "
                                + accesCode.getTargetOwner().getSimpleName() + "." + accesCode.getTarget().getName());
                    }
                }
            }
        }
        assertThat(violations).as("identite.web ne doit toucher ni puits de secret ni repository").isEmpty();
    }

    /**
     * Angle mort (b) : écritures de secrets qui contourneraient les services. Règles vérifiables :
     * (1) aucune méthode {@code @Modifying} dans {@code identite.repository}, et aucune requête {@code @Query}
     * d'écriture (update/delete/insert) ; (2) aucune classe d'{@code identite} hors repository n'utilise
     * {@code JdbcTemplate}/{@code EntityManager} ; (3) le champ {@code motDePasseHache} (Utilisateur, jetons) n'est
     * écrit que depuis sa propre classe, et dans {@code Utilisateur} uniquement par le constructeur et
     * {@code changerMotDePasseHache} (deux puits déjà surveillés).
     *
     * <p>Limite assumée : ArchUnit ne voit pas le SQL construit dynamiquement ni une écriture faite depuis un autre
     * module (interdite par la règle 1 de CLAUDE.md, et les entités d'identite ne sont pas exposées en écriture hors
     * du module) ; une requête native d'écriture ne serait détectée que par (1).</p>
     */
    @Test
    void identiteRepository_n_ecritAucunSecret_etLesChampsSecretsNeSontEcritsQueParLeurClasse() {
        JavaClasses classes = classesIdentite();
        List<String> violations = new ArrayList<>();

        for (JavaClass classe : classes) {
            if (classe.getPackageName().equals(PAQUET_IDENTITE + ".repository")) {
                for (JavaMethod methode : classe.getMethods()) {
                    if (methode.isAnnotatedWith(org.springframework.data.jpa.repository.Modifying.class)) {
                        violations.add(classe.getSimpleName() + "." + methode.getName() + " est @Modifying");
                    }
                    methode.tryGetAnnotationOfType(org.springframework.data.jpa.repository.Query.class).ifPresent(requete -> {
                        String texte = requete.value().strip().toLowerCase(java.util.Locale.ROOT);
                        if (texte.startsWith("update") || texte.startsWith("delete") || texte.startsWith("insert")) {
                            violations.add(classe.getSimpleName() + "." + methode.getName() + " est une requête d'écriture");
                        }
                    });
                }
            } else {
                for (JavaCodeUnit unite : classe.getCodeUnits()) {
                    for (JavaAccess<?> acces : unite.getAccessesFromSelf()) {
                        String cible = acces.getTargetOwner().getName();
                        if (cible.equals("org.springframework.jdbc.core.JdbcTemplate")
                                || cible.equals("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
                                || cible.equals("jakarta.persistence.EntityManager")) {
                            violations.add(classe.getSimpleName() + "." + unite.getName() + " utilise " + cible);
                        }
                    }
                }
            }
            for (var champ : classe.getFields()) {
                if (!champ.getName().equals("motDePasseHache")) {
                    continue;
                }
                for (var acces : champ.getAccessesToSelf()) {
                    if (acces.getAccessType() != com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType.SET) {
                        continue;
                    }
                    boolean autreClasse = !acces.getOriginOwner().equals(classe);
                    boolean utilisateurNonPuits = classe.getSimpleName().equals("Utilisateur")
                            && !acces.getOrigin().getName().equals("<init>")
                            && !acces.getOrigin().getName().equals("changerMotDePasseHache");
                    if (autreClasse || utilisateurNonPuits) {
                        violations.add(acces.getOrigin().getFullName() + " écrit " + classe.getSimpleName() + ".motDePasseHache");
                    }
                }
            }
        }
        assertThat(violations).as("écritures de secrets hors services").isEmpty();
    }

    @Autowired
    private CreateurCompteAdministrateur createurCompteAdministrateur;
    @Autowired
    private tg.novadigital.edukeys.identite.CreateurCompteEleve createurCompteEleve;

    @Test
    void lesMethodesDeCreationNeVisentJamaisUnCompteExistant() {
        String suffixe = UUID.randomUUID().toString();
        // Comptes existants : super admin, personnel d'ici, élève d'ailleurs (identifiant sans forme d'email).
        String emailSuperAdmin = "sa-cr-" + suffixe + "@edukeys.tg";
        String emailPersonnel = "pers-cr-" + suffixe + "@edukeys.tg";
        String matriculeAilleurs = "mat-cr-" + suffixe;
        Map<String, UUID> existants = new LinkedHashMap<>();
        existants.put(emailSuperAdmin, creerCompte(emailSuperAdmin, true, true, Map.of()));
        existants.put(emailPersonnel, creerCompte(emailPersonnel, false, true, Map.of(ETABLISSEMENT_A, "ENSEIGNANT")));
        existants.put(matriculeAilleurs, creerCompte(matriculeAilleurs, false, true, Map.of(ETABLISSEMENT_B, "ELEVE")));

        List<String> echecs = new ArrayList<>();
        existants.forEach((identifiant, cibleId) -> {
            for (String variante : new String[] {identifiant, identifiant.toUpperCase()}) {
                Snapshot avant = snapshot(cibleId);
                Map<String, Invocation> creations = new LinkedHashMap<>();
                creations.put("creerCompteAvecRoles", id -> utilisateurService.creerCompteAvecRoles(
                        variante, "Intrus", java.util.EnumSet.of(tg.novadigital.edukeys.identite.domain.RoleCode.GESTIONNAIRE), null));
                creations.put("creerCompteAdministrateurInitial", id -> utilisateurService.creerCompteAdministrateurInitial(variante, "Intrus"));
                creations.put("CreateurCompteAdministrateur.creerAdministrateur",
                        id -> createurCompteAdministrateur.creerAdministrateur(ETABLISSEMENT_A, variante, "Intrus"));
                // US-08 : le port de création de compte élève (identifiant = matricule) non plus ne vise jamais un compte existant.
                creations.put("creerCompteEleve", id -> utilisateurService.creerCompteEleve(variante, "Intrus"));
                creations.put("CreateurCompteEleve.creerCompteEleve", id -> createurCompteEleve.creerCompteEleve(variante, "Intrus"));
                creations.forEach((nom, invocation) -> {
                    try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                        assertThatThrownBy(() -> invocation.appeler(cibleId))
                                .as(nom + " avec l'identifiant existant " + variante)
                                .isInstanceOf(tg.novadigital.edukeys.common.exception.ConflitException.class);
                    } catch (AssertionError e) {
                        echecs.add(e.getMessage());
                    }
                });
                if (!avant.equals(snapshot(cibleId))) {
                    echecs.add("le compte existant " + variante + " a été modifié par une méthode de création");
                }
                Long nombre = jdbcTemplate.queryForObject(
                        "select count(*) from utilisateurs where identifiant_connexion = ?", Long.class,
                        tg.novadigital.edukeys.common.securite.IdentifiantConnexion.normaliser(variante));
                if (nombre != 1L) {
                    echecs.add("identifiant " + variante + " porté par " + nombre + " comptes");
                }
            }
        });
        assertThat(echecs).as("Une méthode de création a visé ou dupliqué un compte existant").isEmpty();
    }

    // ------------------------------------------------------------------
    // 2. Chaque méthode bornée refuse, et n'écrit rien
    // ------------------------------------------------------------------

    private record Snapshot(String hache, boolean aChanger, long jetonsActivation, long refreshActifs) {
    }

    private Snapshot snapshot(UUID id) {
        entityManager.flush();
        List<Map<String, Object>> lignes = jdbcTemplate.queryForList(
                "select mot_de_passe_hache, mot_de_passe_a_changer from utilisateurs where id = ?", id);
        if (lignes.isEmpty()) {
            return new Snapshot(null, false, 0, 0); // identifiant inconnu : rien à lire, rien ne doit apparaître
        }
        Map<String, Object> ligne = lignes.get(0);
        long jetons = jdbcTemplate.queryForObject(
                "select count(*) from jetons_activation_compte where utilisateur_id = ?", Long.class, id);
        long refresh = jdbcTemplate.queryForObject(
                "select count(*) from jetons_rafraichissement where utilisateur_id = ? and actif = true", Long.class, id);
        return new Snapshot((String) ligne.get("mot_de_passe_hache"), (Boolean) ligne.get("mot_de_passe_a_changer"), jetons, refresh);
    }

    private UUID creerCompte(String identifiant, boolean superAdmin, boolean actif, Map<UUID, String> affectations) {
        Utilisateur compte = new Utilisateur(
                superAdmin ? identifiant : null, identifiant, passwordEncoder.encode("Password123!"), "Cible " + identifiant, superAdmin);
        if (!actif) {
            compte.desactiver();
        }
        compte = utilisateurRepository.save(compte);
        entityManager.flush();
        for (Map.Entry<UUID, String> affectation : affectations.entrySet()) {
            UUID affectationId = UUID.randomUUID();
            jdbcTemplate.update(
                    "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                            + "values (?, ?, ?, true, now(), now())", affectationId, compte.getId(), affectation.getKey());
            // Valeur = rôles séparés par des virgules (ex. "ADMIN,PARENT").
            for (String role : affectation.getValue().split(",")) {
                jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, role);
            }
        }
        // Une session active : doit survivre à l'appel refusé.
        jdbcTemplate.update(
                "insert into jetons_rafraichissement (id, utilisateur_id, jeton_hache, famille_id, date_expiration, actif, date_creation, date_modification) "
                        + "values (?, ?, ?, ?, now() + interval '1 day', true, now(), now())",
                UUID.randomUUID(), compte.getId(), "h-" + UUID.randomUUID(), UUID.randomUUID());
        entityManager.flush();
        return compte.getId();
    }

    @Test
    void chaqueMethodeBorneeRefuseUnCompteHorsPerimetre_etNEcritRien() {
        String suffixe = UUID.randomUUID().toString();
        Map<String, UUID> cibles = new LinkedHashMap<>();
        cibles.put("eleve d'un autre établissement", creerCompte("mat-b-" + suffixe, false, true, Map.of(ETABLISSEMENT_B, "ELEVE")));
        cibles.put("super admin sans affectation", creerCompte("sa1-" + suffixe + "@edukeys.tg", true, true, Map.of()));
        cibles.put("super admin affecté ici (ELEVE)", creerCompte("sa2-" + suffixe + "@edukeys.tg", true, true, Map.of(ETABLISSEMENT_A, "ELEVE")));
        cibles.put("eleve affecté ici ET ailleurs", creerCompte("mat-ab-" + suffixe, false, true,
                Map.of(ETABLISSEMENT_A, "ELEVE", ETABLISSEMENT_B, "ELEVE")));
        cibles.put("eleve inactif ici", creerCompte("mat-off-" + suffixe, false, false, Map.of(ETABLISSEMENT_A, "ELEVE")));
        cibles.put("identifiant inconnu", UUID.randomUUID());

        Map<String, UUID> comptesDuPersonnelIci = new LinkedHashMap<>();
        comptesDuPersonnelIci.put("compte du personnel ici (ENSEIGNANT)",
                creerCompte("pers-" + suffixe + "@edukeys.tg", false, true, Map.of(ETABLISSEMENT_A, "ENSEIGNANT")));
        // Un rôle PARENT cumulé à un rôle du personnel ne fait pas un « compte de portail » : seul un compte
        // dont TOUS les rôles sont ELEVE/PARENT est éligible (sinon un agent lirait le mot de passe d'un ADMIN).
        comptesDuPersonnelIci.put("enseignant ET parent, affecté seulement ici",
                creerCompte("ens-par-" + suffixe + "@edukeys.tg", false, true, Map.of(ETABLISSEMENT_A, "ENSEIGNANT,PARENT")));
        comptesDuPersonnelIci.put("admin ET parent, affecté seulement ici",
                creerCompte("adm-par-" + suffixe + "@edukeys.tg", false, true, Map.of(ETABLISSEMENT_A, "ADMIN,PARENT")));

        List<String> echecs = new ArrayList<>();
        bornees().forEach((methode, cas) -> {
            Map<String, UUID> aTester = new LinkedHashMap<>(cibles);
            if (cas.refuseUnCompteDuPersonnel()) {
                aTester.putAll(comptesDuPersonnelIci);
            }
            aTester.forEach((scenario, cibleId) -> {
                Snapshot avant = snapshot(cibleId);
                try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
                    assertThatThrownBy(() -> cas.invocation().appeler(cibleId))
                            .as(methode + " sur " + scenario)
                            .isInstanceOfAny(RessourceIntrouvableException.class, AccesInterditException.class);
                } catch (AssertionError e) {
                    echecs.add(methode + " / " + scenario + " : " + e.getMessage());
                }
                Snapshot apres = snapshot(cibleId);
                if (!avant.equals(apres)) {
                    echecs.add(methode + " / " + scenario + " : le compte a été modifié (" + avant + " -> " + apres + ")");
                }
            });
        });
        assertThat(echecs).as("Brèches détectées").isEmpty();
    }

    @Test
    void sansContexteEtablissement_chaqueMethodeBorneeRefuse() {
        UUID cible = creerCompte("mat-ctx-" + UUID.randomUUID(), false, true, Map.of(ETABLISSEMENT_A, "ELEVE"));
        bornees().forEach((methode, cas) -> {
            Snapshot avant = snapshot(cible);
            assertThatThrownBy(() -> cas.invocation().appeler(cible)).as(methode + " sans contexte").isNotNull();
            assertThat(snapshot(cible)).as(methode + " sans contexte : aucune écriture").isEqualTo(avant);
        });
    }

    @Test
    void controleDePositivite_lAppelBorneReussitSurLeBonCompte() {
        // Sans ce contrôle, un refus systématique (ex. bug dans le jeu de données) ferait passer les tests ci-dessus.
        UUID eleve = creerCompte("mat-ok-" + UUID.randomUUID(), false, true, Map.of(ETABLISSEMENT_A, "ELEVE"));
        Snapshot avant = snapshot(eleve);
        try (var portee = ContexteEtablissement.ouvrir(ETABLISSEMENT_A)) {
            String motDePasse = emetteur.emettreAvecExpiration(eleve, expirationValide());
            assertThat(motDePasse).isNotBlank();
            entityManager.flush();
        }
        assertThat(snapshot(eleve)).isNotEqualTo(avant);
    }
}
