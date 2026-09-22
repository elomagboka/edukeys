package tg.novadigital.edukeys.admission.service;

import java.net.http.HttpClient;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tg.novadigital.edukeys.admission.AdmissionProperties;

/**
 * Vérification côté serveur de Cloudflare Turnstile (règle 4 de la spec
 * US-06) : <b>le vrai garde-fou</b> de la pré-inscription publique, exécuté
 * avant tout traitement du formulaire et des fichiers. Son absence, son échec
 * ou l'indisponibilité du service de vérification sont systématiquement un
 * <b>refus</b> — jamais un contournement silencieux en cas d'erreur réseau.
 */
@Service
public class VerificationTurnstileService {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationTurnstileService.class);

    private final AdmissionProperties proprietes;
    private final RestClient restClient;

    public VerificationTurnstileService(AdmissionProperties proprietes) {
        this.proprietes = proprietes;
        // Délai de lecture = délai global de la réponse (JdkClientHttpRequestFactory),
        // et non un délai entre deux octets : un serveur qui répond goutte à
        // goutte ne peut donc pas le prolonger indéfiniment.
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(proprietes.getTurnstile().getDelaiConnexion())
                .build();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(client);
        fabrique.setReadTimeout(proprietes.getTurnstile().getDelaiLecture());
        this.restClient = RestClient.builder().requestFactory(fabrique).build();
    }

    /** {@code false} sur tout jeton absent, invalide, ou en cas d'échec (réseau, timeout, erreur du service Turnstile) — jamais {@code true} par défaut. */
    public boolean verifier(String jeton, String adresseIp) {
        if (jeton == null || jeton.isBlank()) {
            return false;
        }
        String cleSecrete = proprietes.getTurnstile().getCleSecrete();
        if (cleSecrete == null || cleSecrete.isBlank()) {
            LOG.warn("verification_turnstile_indisponible motif=cle_secrete_absente");
            return false;
        }

        MultiValueMap<String, String> corps = new LinkedMultiValueMap<>();
        corps.add("secret", cleSecrete);
        corps.add("response", jeton);
        if (adresseIp != null) {
            corps.add("remoteip", adresseIp);
        }

        try {
            ReponseTurnstile reponse = restClient.post()
                    .uri(proprietes.getTurnstile().getUrlVerification())
                    .body(corps)
                    .retrieve()
                    .body(ReponseTurnstile.class);
            return reponse != null && reponse.success();
        } catch (RuntimeException e) {
            // Indisponibilité du service de vérification == refus (règle 4) : jamais de contournement silencieux.
            LOG.warn("verification_turnstile_echec motif=appel_echoue");
            return false;
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record ReponseTurnstile(boolean success) {
    }
}
