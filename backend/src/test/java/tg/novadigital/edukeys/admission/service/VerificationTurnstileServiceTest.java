package tg.novadigital.edukeys.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import tg.novadigital.edukeys.admission.AdmissionProperties;

/**
 * Comportement réel de {@link VerificationTurnstileService} face à
 * l'indisponibilité du service Turnstile (critère 2 de la couverture US-06) :
 * pas de serveur HTTP simulé (ni WireMock ni MockWebServer, proscrits) — le
 * client REST interne est remplacé par un mock injecté par réflexion, seul
 * moyen de tester le vrai {@code catch} de la classe sans dépendance
 * supplémentaire. Le reste des scénarios Turnstile (jeton absent, invalide,
 * chemin nominal) est couvert par un double du <b>service</b> entier au niveau
 * intégration ({@code SoumissionPubliqueAdmissionIntegrationTest}).
 */
class VerificationTurnstileServiceTest {

    @Test
    void doitRefuser_quandJetonAbsent() {
        VerificationTurnstileService service = new VerificationTurnstileService(new AdmissionProperties());
        assertThat(service.verifier(null, "41.207.0.1")).isFalse();
        assertThat(service.verifier("   ", "41.207.0.1")).isFalse();
    }

    @Test
    void doitRefuser_quandCleSecreteAbsente() {
        AdmissionProperties proprietes = new AdmissionProperties();
        // cleSecrete non renseignée : comportement par défaut, jamais un passage silencieux.
        VerificationTurnstileService service = new VerificationTurnstileService(proprietes);
        assertThat(service.verifier("un-jeton", "41.207.0.1")).isFalse();
    }

    @Test
    void doitRefuser_quandLappelReseauEchoue_jamaisUnContournementSilencieux() throws Exception {
        AdmissionProperties proprietes = new AdmissionProperties();
        proprietes.getTurnstile().setCleSecrete("cle-secrete-test");
        VerificationTurnstileService service = new VerificationTurnstileService(proprietes);

        RestClient restClientMoque = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        when(restClientMoque.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenThrow(new ResourceAccessException("connexion refusée (simulation timeout réseau)"));

        Field champRestClient = VerificationTurnstileService.class.getDeclaredField("restClient");
        champRestClient.setAccessible(true);
        champRestClient.set(service, restClientMoque);

        assertThat(service.verifier("un-jeton", "41.207.0.1")).isFalse();
    }

    /**
     * B2 (revue US-06) : un Cloudflare qui tarde ne doit pas immobiliser le
     * thread de la requête. Vrai appel HTTP vers un serveur du JDK qui ne
     * répond pas avant 10 s : le service doit refuser au bout du délai de
     * lecture, pas attendre la réponse.
     */
    @Test
    void doitRefuser_auBoutDuDelaiDeLecture_quandLeServiceTarde() throws Exception {
        CountDownLatch liberer = new CountDownLatch(1);
        HttpServer serveurLent = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveurLent.createContext("/siteverify", echange -> {
            try {
                liberer.await(10, TimeUnit.SECONDS);
                byte[] corps = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
                echange.sendResponseHeaders(200, corps.length);
                echange.getResponseBody().write(corps);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                echange.close();
            }
        });
        serveurLent.start();
        try {
            AdmissionProperties proprietes = new AdmissionProperties();
            proprietes.getTurnstile().setCleSecrete("cle-secrete-test");
            proprietes.getTurnstile().setUrlVerification(
                    "http://127.0.0.1:" + serveurLent.getAddress().getPort() + "/siteverify");
            proprietes.getTurnstile().setDelaiLecture(Duration.ofMillis(300));
            VerificationTurnstileService service = new VerificationTurnstileService(proprietes);

            long debut = System.nanoTime();
            boolean resultat = service.verifier("un-jeton", "41.207.0.1");
            Duration duree = Duration.ofNanos(System.nanoTime() - debut);

            // Le serveur aurait répondu success=true : seul le délai peut expliquer le refus.
            assertThat(resultat).isFalse();
            assertThat(duree).isLessThan(Duration.ofSeconds(3));
        } finally {
            liberer.countDown();
            serveurLent.stop(0);
        }
    }
}
