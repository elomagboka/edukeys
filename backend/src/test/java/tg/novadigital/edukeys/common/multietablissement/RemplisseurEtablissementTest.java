package tg.novadigital.edukeys.common.multietablissement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;
import tg.novadigital.edukeys.common.domain.RemplisseurEtablissement;

/**
 * Unitaire des règles R4.1 à R4.5 (sous-tâche 7, T-05) — y compris R4.4 sur
 * mise à jour ({@code @PreUpdate}, ajouté en sous-tâche 11 pour combler la
 * lacune du cas C8 d'{@code IsolationEtablissementTest} : {@code save()} sur
 * une entité déjà identifiée route vers {@code EntityManager.merge()}, jamais
 * {@code persist()}, donc {@code @PrePersist} seul ne voyait jamais passer
 * une modification inter-établissement). Utilise une entité de test minimale
 * plutôt qu'une entité JPA réelle : ni {@code @PrePersist} ni
 * {@code @PreUpdate} n'exigent de contexte de persistance pour être exercés,
 * seul le comportement métier du listener est ici sous test.
 */
class RemplisseurEtablissementTest {

    private final RemplisseurEtablissement remplisseur = new RemplisseurEtablissement();
    private final UUID etablissementA = UUID.randomUUID();
    private final UUID etablissementB = UUID.randomUUID();

    private Logger loggerSecurite;
    private ListAppender<ILoggingEvent> appenderSecurite;

    /**
     * {@code setEtablissementId} est package-private dans
     * {@code common.domain} : seul {@link RemplisseurEtablissement} l'atteint.
     * Une entité pré-renseignée (R4.3, R4.4) se monte donc par le constructeur
     * {@code protected}, accessible à une sous-classe — exactement le chemin
     * qu'emprunterait une vraie entité métier.
     */
    private static class EntiteDeTest extends EntiteEtablissement {
        EntiteDeTest() {
            super();
        }

        EntiteDeTest(UUID etablissementId) {
            super(etablissementId);
        }
    }

    @BeforeEach
    void configurerAppenderSecurite() {
        loggerSecurite = (Logger) LoggerFactory.getLogger("SECURITE");
        appenderSecurite = new ListAppender<>();
        appenderSecurite.start();
        loggerSecurite.addAppender(appenderSecurite);
    }

    @AfterEach
    void nettoyer() {
        ContexteEtablissement.courant().ifPresent(p -> ContexteEtablissement.restaurer(null));
        loggerSecurite.detachAppender(appenderSecurite);
    }

    private List<String> messagesJournalises() {
        return appenderSecurite.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void r4_1_remplit_avec_l_etablissement_courant_quand_le_champ_est_nul() {
        EntiteDeTest entite = new EntiteDeTest();
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            remplisseur.remplir(entite);
        }
        assertThat(entite.getEtablissementId()).isEqualTo(etablissementA);
    }

    @Test
    void r4_2_refuse_quand_le_champ_est_nul_et_aucun_contexte_ouvert() {
        EntiteDeTest entite = new EntiteDeTest();
        assertThatThrownBy(() -> remplisseur.remplir(entite))
                .isInstanceOf(ContexteEtablissementAbsentException.class);
        assertThat(entite.getEtablissementId()).isNull();
    }

    @Test
    void r4_3_laisse_inchange_quand_le_champ_egale_le_contexte_courant() {
        EntiteDeTest entite = new EntiteDeTest(etablissementA);
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            remplisseur.remplir(entite);
        }
        assertThat(entite.getEtablissementId()).isEqualTo(etablissementA);
    }

    @Test
    void r4_4_refuse_quand_le_champ_differe_du_contexte_courant() {
        EntiteDeTest entite = new EntiteDeTest(etablissementB);
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            assertThatThrownBy(() -> remplisseur.remplir(entite))
                    .isInstanceOf(EcritureInterEtablissementRefuseeException.class)
                    .hasMessageContaining(etablissementA.toString())
                    .hasMessageContaining(etablissementB.toString());
        }
    }

    @Test
    void r4_4_refuse_quand_le_champ_est_deja_renseigne_sans_contexte_ouvert() {
        EntiteDeTest entite = new EntiteDeTest(etablissementB);
        assertThatThrownBy(() -> remplisseur.remplir(entite))
                .isInstanceOf(EcritureInterEtablissementRefuseeException.class);
    }

    @Test
    void r4_5_traite_etablissement_nil_comme_une_absence_de_contexte() {
        EntiteDeTest entite = new EntiteDeTest();
        try (var portee = ContexteEtablissement.ouvrir(ContexteEtablissement.ETABLISSEMENT_NIL)) {
            assertThatThrownBy(() -> remplisseur.remplir(entite))
                    .isInstanceOf(ContexteEtablissementAbsentException.class);
        }
    }

    // ------------------------------------------------------------------
    // R4.4 sur mise à jour (@PreUpdate) — sous-tâche 11
    // ------------------------------------------------------------------

    @Test
    void r4_4_update_accepte_quand_le_champ_egale_le_contexte_courant() {
        EntiteDeTest entite = new EntiteDeTest(etablissementA);
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            remplisseur.verifierAvantMiseAJour(entite);
        }
        assertThat(entite.getEtablissementId()).isEqualTo(etablissementA);
        assertThat(messagesJournalises()).isEmpty();
    }

    @Test
    void r4_4_update_refuse_et_journalise_quand_le_champ_differe_du_contexte_courant() {
        EntiteDeTest entite = new EntiteDeTest(etablissementB);
        try (var portee = ContexteEtablissement.ouvrir(etablissementA)) {
            assertThatThrownBy(() -> remplisseur.verifierAvantMiseAJour(entite))
                    .isInstanceOf(EcritureInterEtablissementRefuseeException.class)
                    .hasMessageContaining(etablissementA.toString())
                    .hasMessageContaining(etablissementB.toString());
        }

        List<String> messages = messagesJournalises();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains(
                "motif=inter_etablissement",
                "entite=" + EntiteDeTest.class.getSimpleName(),
                "etablissementAttendu=" + etablissementA,
                "etablissementRencontre=" + etablissementB);
    }

    /**
     * Aucun contexte ouvert au moment de la mise à jour : comme pour R4.4 côté
     * {@code @PrePersist} ({@code r4_4_refuse_quand_le_champ_est_deja_renseigne_sans_contexte_ouvert}),
     * l'absence de contexte ne peut pas confirmer la légitimité de la
     * modification — refusée, journalisée avec {@code etablissementAttendu=null}
     * (cohérent avec {@code JournalSecurite.ecritureInterEtablissementRefusee},
     * qui accepte un {@code etablissementAttendu} nul).
     */
    @Test
    void r4_4_update_refuse_et_journalise_quand_aucun_contexte_ouvert() {
        EntiteDeTest entite = new EntiteDeTest(etablissementB);

        assertThatThrownBy(() -> remplisseur.verifierAvantMiseAJour(entite))
                .isInstanceOf(EcritureInterEtablissementRefuseeException.class);

        List<String> messages = messagesJournalises();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains(
                "motif=inter_etablissement",
                "entite=" + EntiteDeTest.class.getSimpleName(),
                "etablissementAttendu=null",
                "etablissementRencontre=" + etablissementB);
    }

    /**
     * R4.5 : la désactivation ciblée du filtre pour {@code SUPER_ADMIN}
     * (ADR-0002 §5) ne porte que sur la <em>lecture</em> des entités
     * établissement/utilisateur — jamais sur l'écriture de données métier.
     * {@link RemplisseurEtablissement} n'a d'ailleurs aucune connaissance des
     * rôles : il ne lit que {@link PerimetreEtablissement#etablissementId()},
     * jamais {@code Origine}. Un contexte ouvert avec
     * {@link PerimetreEtablissement.Origine#BASCULE_SUPER_ADMIN} doit donc
     * être traité à l'identique de {@code JETON}/{@code EXPLICITE} : aucune
     * dérogation en écriture.
     */
    @Test
    void r4_5_super_admin_ne_beneficie_d_aucune_derogation_en_ecriture() {
        EntiteDeTest entite = new EntiteDeTest(etablissementB);
        try (var portee = ContexteEtablissement.ouvrir(etablissementA, PerimetreEtablissement.Origine.BASCULE_SUPER_ADMIN)) {
            assertThatThrownBy(() -> remplisseur.verifierAvantMiseAJour(entite))
                    .isInstanceOf(EcritureInterEtablissementRefuseeException.class)
                    .hasMessageContaining(etablissementA.toString())
                    .hasMessageContaining(etablissementB.toString());
        }

        List<String> messages = messagesJournalises();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains(
                "motif=inter_etablissement",
                "entite=" + EntiteDeTest.class.getSimpleName(),
                "etablissementAttendu=" + etablissementA,
                "etablissementRencontre=" + etablissementB);
    }
}
