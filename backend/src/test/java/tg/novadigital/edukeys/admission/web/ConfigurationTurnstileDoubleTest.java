package tg.novadigital.edukeys.admission.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import tg.novadigital.edukeys.admission.AdmissionProperties;

/** Remplace le bean {@code VerificationTurnstileService} par un double pilotable (US-06), voir {@link VerificationTurnstileServiceDouble}. */
@TestConfiguration
public class ConfigurationTurnstileDoubleTest {

    @Bean
    @Primary
    public VerificationTurnstileServiceDouble verificationTurnstileServiceDouble(AdmissionProperties proprietes) {
        return new VerificationTurnstileServiceDouble(proprietes);
    }
}
