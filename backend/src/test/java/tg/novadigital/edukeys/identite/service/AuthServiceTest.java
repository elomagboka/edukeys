package tg.novadigital.edukeys.identite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tg.novadigital.edukeys.common.exception.AccesInterditException;
import tg.novadigital.edukeys.common.exception.IdentifiantsInvalidesException;
import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.RoleCode;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.AffectationEtablissementRepository;
import tg.novadigital.edukeys.identite.repository.JetonRafraichissementRepository;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;
import tg.novadigital.edukeys.identite.security.JwtService;
import tg.novadigital.edukeys.identite.web.JetonsReponseDto;

class AuthServiceTest {

    private UtilisateurRepository utilisateurRepository;
    private AffectationEtablissementRepository affectationEtablissementRepository;
    private JetonRafraichissementRepository jetonRafraichissementRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private JetonHacheur jetonHacheur;
    private AuthService authService;

    private Logger loggerSecurite;
    private ListAppender<ILoggingEvent> appenderSecurite;

    @BeforeEach
    void configurer() {
        utilisateurRepository = mock(UtilisateurRepository.class);
        affectationEtablissementRepository = mock(AffectationEtablissementRepository.class);
        jetonRafraichissementRepository = mock(JetonRafraichissementRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        jetonHacheur = mock(JetonHacheur.class);

        authService = new AuthService(
                utilisateurRepository,
                affectationEtablissementRepository,
                jetonRafraichissementRepository,
                passwordEncoder,
                jwtService,
                jetonHacheur);

        loggerSecurite = (Logger) LoggerFactory.getLogger("SECURITE");
        appenderSecurite = new ListAppender<>();
        appenderSecurite.start();
        loggerSecurite.addAppender(appenderSecurite);
    }

    @AfterEach
    void nettoyerAppender() {
        loggerSecurite.detachAppender(appenderSecurite);
    }

    private Utilisateur unUtilisateur() {
        return new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
    }

    @Test
    void refuseLaConnexion_quandEmailInconnu() {
        when(utilisateurRepository.findByEmailAndActifTrue("inconnu@edukeys.tg")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.connecter("inconnu@edukeys.tg", "peu-importe", "203.0.113.1"))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void executeQuandMemeBCrypt_quandEmailInconnu() {
        // Un email inconnu doit coûter le même calcul BCrypt qu'un mauvais mot de
        // passe, sinon le temps de réponse distingue les deux cas (T-04, lot 2 n°6).
        when(utilisateurRepository.findByEmailAndActifTrue("inconnu@edukeys.tg")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.connecter("inconnu@edukeys.tg", "peu-importe", "203.0.113.1"))
                .isInstanceOf(IdentifiantsInvalidesException.class);

        verify(passwordEncoder).matches(eq("peu-importe"), any());
    }

    @Test
    void refuseLaConnexion_quandMotDePasseIncorrect() {
        Utilisateur utilisateur = unUtilisateur();
        when(utilisateurRepository.findByEmailAndActifTrue(utilisateur.getEmail())).thenReturn(java.util.Optional.of(utilisateur));
        when(passwordEncoder.matches("mauvais", utilisateur.getMotDePasseHache())).thenReturn(false);

        assertThatThrownBy(() -> authService.connecter(utilisateur.getEmail(), "mauvais", "203.0.113.1"))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void neJournalisePasLEmailEnClair_quandCompteExistantOuInconnu() {
        Utilisateur utilisateur = unUtilisateur();
        when(utilisateurRepository.findByEmailAndActifTrue(utilisateur.getEmail())).thenReturn(java.util.Optional.of(utilisateur));
        when(passwordEncoder.matches("mauvais", utilisateur.getMotDePasseHache())).thenReturn(false);
        when(utilisateurRepository.findByEmailAndActifTrue("inconnu@edukeys.tg")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.connecter(utilisateur.getEmail(), "mauvais", "203.0.113.1"))
                .isInstanceOf(IdentifiantsInvalidesException.class);
        assertThatThrownBy(() -> authService.connecter("inconnu@edukeys.tg", "peu-importe", "203.0.113.1"))
                .isInstanceOf(IdentifiantsInvalidesException.class);

        List<String> messages = appenderSecurite.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

        assertThat(messages).hasSize(2);
        assertThat(messages).noneMatch(message -> message.contains(utilisateur.getEmail()));
        assertThat(messages).noneMatch(message -> message.contains("inconnu@edukeys.tg"));

        assertThat(messages.get(0)).contains("motif=mot_de_passe_incorrect", "utilisateurId=" + utilisateur.getId());
        assertThat(messages.get(1)).contains("motif=compte_inconnu", "empreinte=");
    }

    @Test
    void cumuleLesPermissionsDesRolesDeLAffectation_casDeReferenceEnseignantParent() {
        Utilisateur utilisateur = unUtilisateur();
        when(utilisateurRepository.findByEmailAndActifTrue(utilisateur.getEmail())).thenReturn(java.util.Optional.of(utilisateur));
        when(passwordEncoder.matches("bonMotDePasse", utilisateur.getMotDePasseHache())).thenReturn(true);

        UUID etablissementId = UUID.randomUUID();
        AffectationEtablissement affectation = new AffectationEtablissement(
                utilisateur, etablissementId, Set.of(RoleCode.ENSEIGNANT, RoleCode.PARENT));

        when(affectationEtablissementRepository.findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(any()))
                .thenReturn(List.of(affectation));
        when(jetonHacheur.genererJetonEnClair()).thenReturn("jeton-clair");
        when(jetonHacheur.hacher("jeton-clair")).thenReturn("jeton-hache");
        when(jwtService.genererAccessToken(any(), any(), any())).thenReturn("access-token");

        JetonsReponseDto reponse = authService.connecter(utilisateur.getEmail(), "bonMotDePasse", "203.0.113.1");

        assertThat(reponse.roles()).containsExactlyInAnyOrder("ENSEIGNANT", "PARENT");
        assertThat(reponse.etablissementId()).isEqualTo(etablissementId);
        assertThat(reponse.accessToken()).isEqualTo("access-token");
        assertThat(reponse.refreshToken()).isEqualTo("jeton-clair");
    }

    @Test
    void neRenvoieAucunEtablissement_quandUtilisateurSansAffectation() {
        Utilisateur utilisateur = unUtilisateur();
        when(utilisateurRepository.findByEmailAndActifTrue(utilisateur.getEmail())).thenReturn(java.util.Optional.of(utilisateur));
        when(passwordEncoder.matches("bonMotDePasse", utilisateur.getMotDePasseHache())).thenReturn(true);
        when(affectationEtablissementRepository.findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(any()))
                .thenReturn(List.of());
        when(jetonHacheur.genererJetonEnClair()).thenReturn("jeton-clair");
        when(jetonHacheur.hacher(any())).thenReturn("jeton-hache");
        when(jwtService.genererAccessToken(any(), any(), any())).thenReturn("access-token");

        JetonsReponseDto reponse = authService.connecter(utilisateur.getEmail(), "bonMotDePasse", "203.0.113.1");

        assertThat(reponse.etablissementId()).isNull();
        assertThat(reponse.roles()).isEmpty();
    }

    @Test
    void refuseLeRafraichissement_quandJetonInconnu() {
        when(jetonHacheur.hacher("jeton")).thenReturn("hache");
        when(jetonRafraichissementRepository.findByJetonHache("hache")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> authService.rafraichir("jeton"))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void refuseLeRafraichissement_quandJetonExpire() {
        Utilisateur utilisateur = unUtilisateur();
        JetonRafraichissement jetonExpire = new JetonRafraichissement(utilisateur, "hache", Instant.now().minusSeconds(60));

        when(jetonHacheur.hacher("jeton")).thenReturn("hache");
        when(jetonRafraichissementRepository.findByJetonHache("hache")).thenReturn(java.util.Optional.of(jetonExpire));

        assertThatThrownBy(() -> authService.rafraichir("jeton"))
                .isInstanceOf(IdentifiantsInvalidesException.class);
    }

    @Test
    void refuseLeRafraichissement_quandCompteDesactive() {
        Utilisateur utilisateur = unUtilisateur();
        utilisateur.desactiver();
        JetonRafraichissement jeton = new JetonRafraichissement(utilisateur, "hache", Instant.now().plusSeconds(3600));

        when(jetonHacheur.hacher("jeton")).thenReturn("hache");
        when(jetonRafraichissementRepository.findByJetonHache("hache")).thenReturn(java.util.Optional.of(jeton));

        assertThatThrownBy(() -> authService.rafraichir("jeton"))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    void revoqueLAncienJeton_quandRafraichissementReussi() {
        Utilisateur utilisateur = unUtilisateur();
        JetonRafraichissement jeton = new JetonRafraichissement(utilisateur, "hache", Instant.now().plusSeconds(3600));

        when(jetonHacheur.hacher("jeton")).thenReturn("hache");
        when(jetonRafraichissementRepository.findByJetonHache("hache")).thenReturn(java.util.Optional.of(jeton));
        when(affectationEtablissementRepository.findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(any()))
                .thenReturn(List.of());
        when(jetonHacheur.genererJetonEnClair()).thenReturn("nouveau-jeton-clair");
        when(jetonHacheur.hacher("nouveau-jeton-clair")).thenReturn("nouveau-jeton-hache");
        when(jwtService.genererAccessToken(any(), any(), any())).thenReturn("nouveau-access-token");

        authService.rafraichir("jeton");

        assertThat(jeton.isActif()).isFalse();
        verify(jetonRafraichissementRepository).save(jeton);
    }

    @Test
    void revoqueToutesLesFamillesDeJetonsActives_quandUnJetonDejaTourneEstRepresente() {
        Utilisateur utilisateur = unUtilisateur();
        UUID familleId = UUID.randomUUID();
        JetonRafraichissement jetonDejaTourne =
                new JetonRafraichissement(utilisateur, "hache-perime", Instant.now().plusSeconds(3600), familleId);
        jetonDejaTourne.desactiver();

        JetonRafraichissement jetonSuivantEncoreActif =
                new JetonRafraichissement(utilisateur, "hache-suivant", Instant.now().plusSeconds(3600), familleId);

        when(jetonHacheur.hacher("jeton-rejoue")).thenReturn("hache-perime");
        when(jetonRafraichissementRepository.findByJetonHache("hache-perime"))
                .thenReturn(java.util.Optional.of(jetonDejaTourne));
        when(jetonRafraichissementRepository.findByFamilleIdAndActifTrue(familleId))
                .thenReturn(List.of(jetonSuivantEncoreActif));

        assertThatThrownBy(() -> authService.rafraichir("jeton-rejoue"))
                .isInstanceOf(IdentifiantsInvalidesException.class);

        // Le jeton suivant, pourtant encore actif et non expiré, doit être coupé
        // lui aussi : c'est toute la chaîne qui est compromise, pas un seul maillon.
        assertThat(jetonSuivantEncoreActif.isActif()).isFalse();
        verify(jetonRafraichissementRepository).save(jetonSuivantEncoreActif);
    }

    @Test
    void conserveLEtablissementActifDuJetonPresente_quandRafraichissementReussi() {
        Utilisateur utilisateur = unUtilisateur();
        UUID etablissementActif = UUID.randomUUID();
        UUID etablissementAutre = UUID.randomUUID();
        JetonRafraichissement jeton = new JetonRafraichissement(
                utilisateur, "hache", Instant.now().plusSeconds(3600), UUID.randomUUID(), etablissementActif);

        AffectationEtablissement affectationAncienne = new AffectationEtablissement(
                utilisateur, etablissementAutre, Set.of(RoleCode.PARENT));
        AffectationEtablissement affectationActive = new AffectationEtablissement(
                utilisateur, etablissementActif, Set.of(RoleCode.DIRECTION));

        when(jetonHacheur.hacher("jeton")).thenReturn("hache");
        when(jetonRafraichissementRepository.findByJetonHache("hache")).thenReturn(java.util.Optional.of(jeton));
        // Le premier affecté n'est pas celui du jeton présenté : sans mémorisation
        // de l'établissement actif, le refresh reviendrait dessus silencieusement.
        when(affectationEtablissementRepository.findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(any()))
                .thenReturn(List.of(affectationAncienne, affectationActive));
        when(jetonHacheur.genererJetonEnClair()).thenReturn("nouveau-jeton-clair");
        when(jetonHacheur.hacher("nouveau-jeton-clair")).thenReturn("nouveau-jeton-hache");
        when(jwtService.genererAccessToken(any(), any(), any())).thenReturn("nouveau-access-token");

        JetonsReponseDto reponse = authService.rafraichir("jeton");

        assertThat(reponse.etablissementId()).isEqualTo(etablissementActif);
        assertThat(reponse.roles()).containsExactly("DIRECTION");
    }

    @Test
    void basculeVersLEtablissementDemande_quandUneAffectationActiveYExiste() {
        Utilisateur utilisateur = unUtilisateur();
        UUID utilisateurId = UUID.randomUUID();
        UUID etablissementCible = UUID.randomUUID();
        AffectationEtablissement affectation = new AffectationEtablissement(
                utilisateur, etablissementCible, Set.of(RoleCode.GESTIONNAIRE));

        when(utilisateurRepository.findById(utilisateurId)).thenReturn(java.util.Optional.of(utilisateur));
        when(affectationEtablissementRepository.existsByUtilisateurIdAndEtablissementIdAndActifTrue(
                utilisateurId, etablissementCible)).thenReturn(true);
        when(affectationEtablissementRepository.findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(any()))
                .thenReturn(List.of(affectation));
        when(jetonHacheur.genererJetonEnClair()).thenReturn("jeton-clair");
        when(jetonHacheur.hacher(any())).thenReturn("jeton-hache");
        when(jwtService.genererAccessToken(any(), any(), any())).thenReturn("access-token");

        JetonsReponseDto reponse = authService.basculerEtablissement(utilisateurId, etablissementCible);

        assertThat(reponse.etablissementId()).isEqualTo(etablissementCible);
        assertThat(reponse.roles()).containsExactly("GESTIONNAIRE");
    }

    @Test
    void refuseLaBascule_quandAucuneAffectationActiveSurLetablissementCible() {
        Utilisateur utilisateur = unUtilisateur();
        UUID utilisateurId = UUID.randomUUID();
        UUID etablissementCible = UUID.randomUUID();

        when(utilisateurRepository.findById(utilisateurId)).thenReturn(java.util.Optional.of(utilisateur));
        when(affectationEtablissementRepository.existsByUtilisateurIdAndEtablissementIdAndActifTrue(
                utilisateurId, etablissementCible)).thenReturn(false);

        assertThatThrownBy(() -> authService.basculerEtablissement(utilisateurId, etablissementCible))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    void autoriseSuperAdminABasculer_memeSansAffectationSurLetablissementCible() {
        Utilisateur superAdmin = new Utilisateur("super@edukeys.tg", "hash", "Super Admin", true);
        UUID utilisateurId = UUID.randomUUID();
        UUID etablissementCible = UUID.randomUUID();

        when(utilisateurRepository.findById(utilisateurId)).thenReturn(java.util.Optional.of(superAdmin));
        when(affectationEtablissementRepository.findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(any()))
                .thenReturn(List.of());
        when(jetonHacheur.genererJetonEnClair()).thenReturn("jeton-clair");
        when(jetonHacheur.hacher(any())).thenReturn("jeton-hache");
        when(jwtService.genererAccessToken(any(), any(), any())).thenReturn("access-token");

        JetonsReponseDto reponse = authService.basculerEtablissement(utilisateurId, etablissementCible);

        assertThat(reponse.etablissementId()).isEqualTo(etablissementCible);
        assertThat(reponse.roles()).containsExactly("SUPER_ADMIN");
    }
}
