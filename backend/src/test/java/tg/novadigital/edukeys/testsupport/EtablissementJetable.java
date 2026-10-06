package tg.novadigital.edukeys.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Motif <b>OBLIGATOIRE</b> des tests d'intégration qui ne peuvent pas reposer sur le rollback transactionnel
 * (CLAUDE.md, règle 4) : un établissement jetable par scénario, créé par l'API réelle, jamais supprimé.
 *
 * <p><b>Quand l'utiliser.</b> Tout test qui touche un compteur (référence d'admission, matricule, et tout
 * compteur futur), et plus généralement tout composant qui commite hors de la transaction du test. Ne pas
 * ajouter une troisième variante : étendre cette classe.</p>
 *
 * <p><b>Pourquoi le rollback est inapplicable.</b> La création d'une ligne de compteur est commitée à part
 * ({@code CompteurReferenceAdmission}, US-06/07) et {@code InscriptionService.inscrire} refuse une transaction
 * active : sous {@code @Transactional}, le test ne verrait pas le comportement réel (verrous, concurrence,
 * commit), ou le service lèverait. Ce que le composant commite ne peut pas être annulé par le test.</p>
 *
 * <p><b>Comment l'isolation est garantie.</b> Un établissement par scénario, au code unique (compteur atomique
 * et suffixe aléatoire base36, format {@code [A-Z0-9]{2,10}}) : deux scénarios, deux classes ou deux threads ne
 * partagent aucune ligne, donc aucun compteur. Entre campagnes, l'isolation vient de la base Testcontainers
 * recréée à chaque campagne : les établissements restants disparaissent avec le conteneur.</p>
 *
 * <p><b>Interdit.</b> Toute suppression (DELETE, {@code deleteAll}, truncate, méthode de nettoyage ajoutée à
 * un repository de production) ; {@code @Transactional} sur la classe de test (il rétablirait la transaction
 * active que le motif cherche à éviter) ; un code d'établissement dérivé de l'horloge ({@code nanoTime}) ; un
 * établissement partagé entre scénarios.</p>
 *
 * <p>Utilisable aussi dans une transaction de test (le flush est alors forcé avant les insertions JDBC), mais
 * c'est un usage toléré, pas le motif.</p>
 */
public final class EtablissementJetable {

    public static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    public static final String MOT_DE_PASSE = "Password123!";

    private static final int LONGUEUR_PREFIXE_MAX = 3;
    private static final int LONGUEUR_COMPTEUR = 3;
    private static final int LONGUEUR_ALEATOIRE = 4;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public EtablissementJetable(MockMvc mockMvc, JdbcTemplate jdbcTemplate, UtilisateurRepository utilisateurRepository,
                                PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    /**
     * Code unique de 10 caractères au plus, conforme à {@code [A-Z0-9]{2,10}} : préfixe nettoyé (3 caractères
     * au plus), compteur atomique base36 sur 3 caractères, suffixe aléatoire base36 sur 4 caractères.
     */
    public static String codeUnique(String prefixe) {
        String p = prefixe.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (p.length() > LONGUEUR_PREFIXE_MAX) {
            p = p.substring(0, LONGUEUR_PREFIXE_MAX);
        }
        int modulo = (int) Math.pow(36, LONGUEUR_COMPTEUR);
        String compteur = Integer.toString(SEQUENCE.getAndIncrement() % modulo, 36);
        String aleatoire = Integer.toString(ALEATOIRE.nextInt((int) Math.pow(36, LONGUEUR_ALEATOIRE)), 36);
        return p + remplir(compteur, LONGUEUR_COMPTEUR) + remplir(aleatoire, LONGUEUR_ALEATOIRE);
    }

    private static String remplir(String valeur, int longueur) {
        return ("0".repeat(longueur) + valeur).substring(valeur.length()).toUpperCase(Locale.ROOT);
    }

    /** Crée un établissement (API, en super-administrateur) au code unique ; renvoie son identifiant. */
    public String creerEtablissement(String prefixeCode) throws Exception {
        String jetonSuperAdmin = connecter(EMAIL_SUPER_ADMIN);
        String code = codeUnique(prefixeCode);
        String reponse = mockMvc.perform(post("/api/v1/etablissements")
                        .header("Authorization", "Bearer " + jetonSuperAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nom":"Établissement %s","typeEtablissement":"COLLEGE",
                                 "ville":"Lomé","email":"contact.%s@edukeys.tg",
                                 "emailAdministrateur":"admin.%s@edukeys.tg","nomCompletAdministrateur":"Admin Test"}
                                """.formatted(code, code, code.toLowerCase(Locale.ROOT), code.toLowerCase(Locale.ROOT))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.etablissement.id");
    }

    /** Code de l'établissement tel qu'enregistré. */
    public String code(String etablissementId) {
        return jdbcTemplate.queryForObject("select code from etablissements where id = ?::uuid", String.class, etablissementId);
    }

    /** Crée un compte affecté à l'établissement avec le rôle donné ; renvoie son jeton d'accès. */
    public String jetonPourRole(String etablissementId, String roleCode) throws Exception {
        Utilisateur compte = utilisateurRepository.save(new Utilisateur(
                "u.jetable." + UUID.randomUUID() + "@edukeys.tg", passwordEncoder.encode(MOT_DE_PASSE), "Utilisateur Jetable Test", false));
        // Dans une transaction de test, l'insertion du compte est différée : la FK de l'affectation (JDBC) l'exige écrite.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            entityManager.flush();
        }
        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compte.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, ?)", affectationId, roleCode);
        return connecter(compte.getEmail());
    }

    public String connecter(String identifiant) throws Exception {
        return connecter(identifiant, MOT_DE_PASSE);
    }

    public String connecter(String identifiant, String motDePasse) throws Exception {
        String reponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifiant":"%s","motDePasse":"%s"}
                                """.formatted(identifiant, motDePasse)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.accessToken");
    }
}
