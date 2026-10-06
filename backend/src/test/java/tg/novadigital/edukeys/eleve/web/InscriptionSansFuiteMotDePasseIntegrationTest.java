package tg.novadigital.edukeys.eleve.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import tg.novadigital.edukeys.common.notification.Notificateur;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;

/**
 * Q10 (US-08) : le mot de passe temporaire n'est affiché qu'une fois, dans la réponse HTTP. Il ne traverse jamais
 * le {@link Notificateur} (donc aucun SMS, email ni notification in-app) ni aucun événement applicatif.
 * Vraies transactions : les écouteurs {@code AFTER_COMMIT} ne se déclenchent pas sous rollback de test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class InscriptionSansFuiteMotDePasseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private ApplicationEvents evenements;
    @MockitoBean
    private Notificateur notificateur;

    @Test
    void leMotDePasseTemporaire_neTransiteParAucuneNotification_niAucunEvenement() throws Exception {
        ScenarioInscription scenario = new ScenarioInscription(mockMvc, jdbcTemplate, utilisateurRepository, passwordEncoder, entityManager);
        ScenarioInscription.Etablissement etab = scenario.etablissementPret("NTF");
        String classeId = scenario.creerClasse(etab.jetonAdmin(), etab.niveauId(), "A", null, null);
        ScenarioInscription.Dossier dossier = scenario.dossierAccepte(etab, "Discret", "Eleve", "2015-04-04");
        // Garde de non-vacuité : la décision d'admission, elle, notifie bien par ce canal (donc le mock voit passer les envois).
        verify(notificateur, atLeastOnce()).envoyer(any(), any(), any());
        clearInvocations(notificateur);
        evenements.clear();

        String reponse = scenario.inscrire(etab.jetonAdmin(), dossier.id(), classeId, dossier.version(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String motDePasse = JsonPath.read(reponse, "$.compte.motDePasseTemporaire");
        String matricule = JsonPath.read(reponse, "$.matricule");
        assertThat(motDePasse).isNotBlank();

        // Aucun envoi du tout ; et, par construction, aucun envoi ne porte le mot de passe.
        assertThat(mockingDetails(notificateur).getInvocations()).isEmpty();
        assertThat(evenements.stream().map(Object::toString).toList())
                .noneMatch(evenement -> evenement.contains(motDePasse));
        assertThat(matricule).startsWith(etab.code());
        Map<String, Object> compte = jdbcTemplate.queryForMap(
                "select mot_de_passe_hache from utilisateurs where identifiant_connexion = ?", matricule.toLowerCase());
        assertThat(compte.get("mot_de_passe_hache").toString()).doesNotContain(motDePasse);
    }
}
