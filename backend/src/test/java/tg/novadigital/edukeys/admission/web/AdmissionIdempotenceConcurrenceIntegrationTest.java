package tg.novadigital.edukeys.admission.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * I5 (revue) : recouvrement d'une violation de {@code uk_demandes_admission_doublon}
 * sous soumission strictement concurrente. Comme {@link
 * SoumissionPubliqueAdmissionServeurReelIntegrationTest}, un vrai serveur
 * Tomcat ({@code RANDOM_PORT}) et de vraies requêtes HTTP concurrentes — la
 * seule façon de faire réellement se chevaucher deux transactions et
 * d'exposer l'écart entre la vérification applicative (étape 1) et l'index
 * unique (étape 2). Pas de {@code @Transactional} de classe ici : les deux
 * threads doivent committer pour de vrai — un établissement dédié par test,
 * jamais nettoyé par suppression (CLAUDE.md, règle 4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ConfigurationTurnstileDoubleTest.class)
class AdmissionIdempotenceConcurrenceIntegrationTest {

    private static final String EMAIL_SUPER_ADMIN = "super.admin@edukeys.tg";
    private static final String MOT_DE_PASSE = "Password123!";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void deuxSoumissionsStrictementConcurrentes_neCreentQuUnSeulDossier_etRenvoientLeMemeAccuse() throws Exception {
        String prefixeCode = "CONC" + (System.nanoTime() % 100000);
        String etablissementId = creerEtablissement(prefixeCode);
        String code = jdbcTemplate.queryForObject("select code from etablissements where id = ?::uuid", String.class, etablissementId);
        String jetonAdmin = creerAdminEtObtenirToken(etablissementId);
        creerEtActiverAnneeScolaire(jetonAdmin);
        String cycleId = creerCycle(jetonAdmin, "Collège", prefixeCode + "C", 1);
        String niveauId = creerNiveau(jetonAdmin, "6ème", prefixeCode + "N", 1, cycleId);
        jdbcTemplate.update("update etablissements set admissions_ouvertes = true where id = ?::uuid", etablissementId);
        // Pré-crée la ligne de compteur de référence (hors périmètre I5, cf.
        // GenerateurReferenceAdmission) : sans elle, les deux threads
        // tenteraient chacun de l'insérer et violeraient
        // uk_compteurs_reference_admission_annee — une course distincte de
        // celle exercée par ce test (uk_demandes_admission_doublon).
        jdbcTemplate.update(
                "insert into compteurs_reference_admission "
                        + "(id, etablissement_id, annee, dernier, actif, date_creation, date_modification) "
                        + "values (gen_random_uuid(), ?::uuid, extract(year from now())::int, 0, true, now(), now())",
                etablissementId);
        entityManager.clear();

        CountDownLatch depart = new CountDownLatch(1);
        Callable<ResponseEntity<String>> soumission = () -> {
            depart.await();
            return soumettre(code, niveauId);
        };

        ExecutorService executeur = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<String>> futureA = executeur.submit(soumission);
            Future<ResponseEntity<String>> futureB = executeur.submit(soumission);
            depart.countDown();

            ResponseEntity<String> reponseA = futureA.get(20, TimeUnit.SECONDS);
            ResponseEntity<String> reponseB = futureB.get(20, TimeUnit.SECONDS);

            assertThat(reponseA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(reponseB.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            String referenceA = JsonPath.read(reponseA.getBody(), "$.reference");
            String referenceB = JsonPath.read(reponseB.getBody(), "$.reference");
            // I5 : les deux appels concurrents reçoivent le même accusé — un seul
            // gagnant de la course, l'autre relit et renvoie sa référence.
            assertThat(referenceB).isEqualTo(referenceA);

            Long nombreDeDossiers = jdbcTemplate.queryForObject(
                    "select count(*) from demandes_admission where etablissement_id = ?::uuid", Long.class, etablissementId);
            assertThat(nombreDeDossiers).isEqualTo(1L);
        } finally {
            executeur.shutdownNow();
        }
    }

    private ResponseEntity<String> soumettre(String code, String niveauId) {
        String json = """
                {
                  "demande": {
                    "niveauId": "%s",
                    "nom": "Concurrence",
                    "prenoms": "Test",
                    "dateNaissance": "2015-01-01",
                    "lieuNaissance": "Lomé",
                    "sexe": "F",
                    "nationalite": "TG",
                    "responsableNom": "Responsable",
                    "responsablePrenoms": "Test",
                    "responsableLien": "PERE",
                    "responsableTelephone": "+22890000088",
                    "responsableEmail": "responsable.conc@example.com",
                    "consentementDonnees": true
                  },
                  "siteWeb": null
                }
                """.formatted(niveauId);

        MultiValueMap<String, Object> corps = new LinkedMultiValueMap<>();
        HttpHeaders enTetesDemande = new HttpHeaders();
        enTetesDemande.setContentType(MediaType.APPLICATION_JSON);
        corps.add("demande", new HttpEntity<>(json.getBytes(StandardCharsets.UTF_8), enTetesDemande));

        HttpHeaders enTetesPiece = new HttpHeaders();
        enTetesPiece.setContentType(MediaType.APPLICATION_PDF);
        byte[] pdf = "%PDF-1.4\n1 0 obj <<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayResource ressourcePdf = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "acte.pdf";
            }
        };
        corps.add("pieces", new HttpEntity<>(ressourcePdf, enTetesPiece));
        corps.add("typesPieces", "ACTE_NAISSANCE");

        HttpHeaders enTetesRequete = new HttpHeaders();
        enTetesRequete.setContentType(MediaType.MULTIPART_FORM_DATA);
        enTetesRequete.set("CF-Turnstile-Response", "jeton-valide");

        return restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/public/etablissements/" + code + "/demandes-admission",
                new HttpEntity<>(corps, enTetesRequete), String.class);
    }

    private String creerEtablissement(String prefixeCode) {
        String jetonSuperAdmin = connecterEtObtenirAccessToken(EMAIL_SUPER_ADMIN);
        String code = prefixeCode + System.nanoTime() % 100000;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jetonSuperAdmin);
        String corps = """
                {"code":"%s","nom":"Établissement %s","typeEtablissement":"COLLEGE",
                 "ville":"Lomé","email":"contact.%s@edukeys.tg",
                 "emailAdministrateur":"admin.%s@edukeys.tg","nomCompletAdministrateur":"Admin Test"}
                """.formatted(code, code, code.toLowerCase(), code.toLowerCase());

        ResponseEntity<String> reponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/etablissements", new HttpEntity<>(corps, headers), String.class);
        return JsonPath.read(reponse.getBody(), "$.etablissement.id");
    }

    private String creerAdminEtObtenirToken(String etablissementId) {
        tg.novadigital.edukeys.identite.domain.Utilisateur compteAdmin = utilisateurRepository.save(
                new tg.novadigital.edukeys.identite.domain.Utilisateur(
                        "admin.us06conc." + UUID.randomUUID() + "@edukeys.tg",
                        passwordEncoder.encode(MOT_DE_PASSE), "Admin US-06 Concurrence Test", false));

        UUID affectationId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification) "
                        + "values (?, ?, ?::uuid, true, now(), now())",
                affectationId, compteAdmin.getId(), etablissementId);
        jdbcTemplate.update("insert into affectation_roles (affectation_id, role_code) values (?, 'ADMIN')", affectationId);

        return connecterEtObtenirAccessToken(compteAdmin.getEmail());
    }

    private String connecterEtObtenirAccessToken(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String corps = """
                {"email":"%s","motDePasse":"%s"}
                """.formatted(email, MOT_DE_PASSE);
        ResponseEntity<String> reponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/login", new HttpEntity<>(corps, headers), String.class);
        return JsonPath.read(reponse.getBody(), "$.accessToken");
    }

    private String creerCycle(String jetonAdmin, String libelle, String code, int rang) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jetonAdmin);
        String corps = """
                {"libelle":"%s","code":"%s","rang":%d}
                """.formatted(libelle, code, rang);
        ResponseEntity<String> reponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/cycles", new HttpEntity<>(corps, headers), String.class);
        return JsonPath.read(reponse.getBody(), "$.id");
    }

    private String creerNiveau(String jetonAdmin, String libelle, String code, int rang, String cycleId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jetonAdmin);
        String corps = """
                {"libelle":"%s","code":"%s","rang":%d,"cycleId":"%s"}
                """.formatted(libelle, code, rang, cycleId);
        ResponseEntity<String> reponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/niveaux", new HttpEntity<>(corps, headers), String.class);
        return JsonPath.read(reponse.getBody(), "$.id");
    }

    private void creerEtActiverAnneeScolaire(String jetonAdmin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jetonAdmin);
        ResponseEntity<String> reponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/annees-scolaires",
                new HttpEntity<>("{\"dateDebut\":\"2026-09-01\",\"dateFin\":\"2027-07-15\"}", headers), String.class);
        String id = JsonPath.read(reponse.getBody(), "$.id");
        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/annees-scolaires/" + id + "/activation",
                new HttpEntity<>(null, headers), String.class);
    }
}
