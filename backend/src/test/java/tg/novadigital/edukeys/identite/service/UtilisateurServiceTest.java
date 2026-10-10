package tg.novadigital.edukeys.identite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.IdentifiantsInvalidesException;
import tg.novadigital.edukeys.common.exception.MotDePasseTemporaireExpireException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissementAbsentException;
import org.springframework.security.crypto.password.PasswordEncoder;

import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;
import tg.novadigital.edukeys.identite.domain.JetonActivationCompte;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.RoleCode;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.AffectationEtablissementRepository;
import tg.novadigital.edukeys.identite.repository.JetonActivationCompteRepository;
import tg.novadigital.edukeys.identite.repository.JetonRafraichissementRepository;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;
import tg.novadigital.edukeys.identite.security.UtilisateurPrincipal;

class UtilisateurServiceTest {

    private UtilisateurRepository utilisateurRepository;
    private JetonRafraichissementRepository jetonRafraichissementRepository;
    private AffectationEtablissementRepository affectationEtablissementRepository;
    private JetonActivationCompteRepository jetonActivationCompteRepository;
    private PasswordEncoder passwordEncoder;
    private JetonHacheur jetonHacheur;
    private GenerateurMotDePasseTemporaire generateurMotDePasseTemporaire;
    private UtilisateurService utilisateurService;

    @BeforeEach
    void configurer() {
        utilisateurRepository = mock(UtilisateurRepository.class);
        jetonRafraichissementRepository = mock(JetonRafraichissementRepository.class);
        affectationEtablissementRepository = mock(AffectationEtablissementRepository.class);
        jetonActivationCompteRepository = mock(JetonActivationCompteRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jetonHacheur = mock(JetonHacheur.class);
        generateurMotDePasseTemporaire = mock(GenerateurMotDePasseTemporaire.class);
        utilisateurService = new UtilisateurService(
                utilisateurRepository, jetonRafraichissementRepository, affectationEtablissementRepository,
                jetonActivationCompteRepository, passwordEncoder, jetonHacheur, generateurMotDePasseTemporaire,
                java.time.Duration.ofDays(14), java.time.Duration.ofDays(240), java.time.Clock.systemUTC());
    }


    @Test
    void desactiverDansEtablissementCourant_leveRessourceIntrouvable_quandAucuneAffectationLocale() {
        UUID etab = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(affectationEtablissementRepository.findByUtilisateurIdAndEtablissementIdAndActifTrue(id, etab))
                .thenReturn(Optional.empty());

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            assertThatThrownBy(() -> utilisateurService.desactiverDansEtablissementCourant(id, UUID.randomUUID()))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Test
    void desactiveLeCompteEtRevoqueSesJetonsActifs_quandPlusAucuneAffectationActive() {
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        UUID id = utilisateur.getId() != null ? utilisateur.getId() : UUID.randomUUID();
        UUID etab = UUID.randomUUID();
        AffectationEtablissement affectation = new AffectationEtablissement(utilisateur, etab, EnumSet.of(RoleCode.ENSEIGNANT));
        when(affectationEtablissementRepository.findByUtilisateurIdAndEtablissementIdAndActifTrue(id, etab))
                .thenReturn(Optional.of(affectation));
        when(affectationEtablissementRepository.existsByUtilisateurIdAndActifTrueAndIdNot(utilisateur.getId(), affectation.getId()))
                .thenReturn(false);

        JetonRafraichissement jeton1 = new JetonRafraichissement(utilisateur, "h1", Instant.now().plusSeconds(3600));
        JetonRafraichissement jeton2 = new JetonRafraichissement(utilisateur, "h2", Instant.now().plusSeconds(3600));
        when(jetonRafraichissementRepository.findByUtilisateurIdAndActifTrue(utilisateur.getId())).thenReturn(List.of(jeton1, jeton2));

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            utilisateurService.desactiverDansEtablissementCourant(id, UUID.randomUUID());
        }

        assertThat(utilisateur.isActif()).isFalse();
        assertThat(jeton1.isActif()).isFalse();
        assertThat(jeton2.isActif()).isFalse();
        verify(jetonRafraichissementRepository).save(jeton1);
        verify(jetonRafraichissementRepository).save(jeton2);
        verify(utilisateurRepository).save(utilisateur);
    }

    // ------------------------------------------------------------------
    // obtenirSoiMeme — revue post-T-05 : la signature ne prend plus un UUID
    // arbitraire mais le principal lui-même, rendant impossible de demander
    // un autre compte que le sien.
    // ------------------------------------------------------------------

    @Test
    void obtenirSoiMeme_renvoieLeCompteDuPrincipal() {
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        UtilisateurPrincipal principal = new UtilisateurPrincipal(utilisateurId, UUID.randomUUID(), Set.of("ADMIN"), false);
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));

        assertThat(utilisateurService.obtenirSoiMeme(principal)).isSameAs(utilisateur);
    }

    @Test
    void obtenirSoiMeme_leveUneExceptionRessourceIntrouvable_quandLeCompteDuPrincipalNexistePlus() {
        UtilisateurPrincipal principal =
                new UtilisateurPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of("ADMIN"), false);
        when(utilisateurRepository.findById(principal.utilisateurId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> utilisateurService.obtenirSoiMeme(principal))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // ------------------------------------------------------------------
    // obtenirDansEtablissementCourant — T-05, sous-tâche 13
    // ------------------------------------------------------------------

    @Test
    void obtenirDansEtablissementCourant_renvoieLeCompte_quandAffecteAEtablissementCourant() {
        UUID etablissementId = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId))
                .thenReturn(true);
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));

        try (var portee = ContexteEtablissement.ouvrir(etablissementId)) {
            assertThat(utilisateurService.obtenirDansEtablissementCourant(utilisateurId)).isSameAs(utilisateur);
        }
    }

    @Test
    void obtenirDansEtablissementCourant_refuse_quandLeCompteAppartientAUnAutreEtablissement() {
        UUID etablissementCourant = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementCourant))
                .thenReturn(false);

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            assertThatThrownBy(() -> utilisateurService.obtenirDansEtablissementCourant(utilisateurId))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Test
    void obtenirDansEtablissementCourant_refuse_quandAucunContexteOuvert() {
        UUID utilisateurId = UUID.randomUUID();

        assertThatThrownBy(() -> utilisateurService.obtenirDansEtablissementCourant(utilisateurId))
                .isInstanceOf(ContexteEtablissementAbsentException.class);
    }

    // ------------------------------------------------------------------
    // listerParEtablissementCourant — T-05, sous-tâche 13
    // ------------------------------------------------------------------

    @Test
    void listerParEtablissementCourant_delegueAuRepositoryAvecLEtablissementDuContexte() {
        UUID etablissementId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Utilisateur utilisateur = new Utilisateur("paul@edukeys.tg", "hash", "Paul Martin", false);
        Page<Utilisateur> page = new PageImpl<>(List.of(utilisateur));
        when(utilisateurRepository.findParEtablissementCourantActif(etablissementId, pageable)).thenReturn(page);

        try (var portee = ContexteEtablissement.ouvrir(etablissementId)) {
            assertThat(utilisateurService.listerParEtablissementCourant(pageable)).containsExactly(utilisateur);
        }
    }

    @Test
    void listerParEtablissementCourant_refuse_quandAucunContexteOuvert() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> utilisateurService.listerParEtablissementCourant(pageable))
                .isInstanceOf(ContexteEtablissementAbsentException.class);
    }

    // ------------------------------------------------------------------
    // creerCompteAvecRoles — revue post-implémentation : faille bloquante
    // corrigée (prise de contrôle d'un compte d'autrui via un email connu
    // globalement, garde-fou de conflit borné à tort à l'établissement
    // courant). Voir la Javadoc de UtilisateurService#creerCompteAvecRoles.
    // ------------------------------------------------------------------

    @Test
    void creerCompteAvecRoles_creeUnNouveauCompte_etRenvoieLeMotDePasseTemporaire_quandEmailInconnu() {
        UUID etablissementId = UUID.randomUUID();
        when(utilisateurRepository.findByEmailAndActifTrue("nouveau@edukeys.tg")).thenReturn(Optional.empty());
        when(generateurMotDePasseTemporaire.generer()).thenReturn("MotDePasseTmp1");
        when(passwordEncoder.encode("MotDePasseTmp1")).thenReturn("hash-bcrypt");
        when(jetonHacheur.hacher("MotDePasseTmp1")).thenReturn("hash-sha256");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));
        when(affectationEtablissementRepository.save(any(AffectationEtablissement.class))).thenAnswer(inv -> inv.getArgument(0));

        try (var portee = ContexteEtablissement.ouvrir(etablissementId)) {
            UtilisateurService.CompteCree resultat = utilisateurService.creerCompteAvecRoles(
                    "Nouveau@Edukeys.tg", "Nouveau Compte", Set.of(RoleCode.GESTIONNAIRE), null);

            assertThat(resultat.motDePasseTemporaire()).isEqualTo("MotDePasseTmp1");
            assertThat(resultat.utilisateur().getMotDePasseHache()).isEqualTo("hash-bcrypt");
            assertThat(resultat.utilisateur().isMotDePasseAChanger()).isTrue();
            assertThat(resultat.affectation().getEtablissementId()).isEqualTo(etablissementId);
        }

        verify(jetonActivationCompteRepository).save(any(JetonActivationCompte.class));
    }

    /**
     * Suite de la revue (3e passe, tranchée par le donneur d'ordre) : le
     * rattachement est supprimé, pas seulement verrouillé. Un email déjà
     * porté par un compte actif — sur l'établissement courant, sur un autre
     * établissement, ou un compte {@code superAdmin} — est refusé (409) avec
     * un message unique, jamais une affectation ajoutée silencieusement.
     */
    @Test
    void creerCompteAvecRoles_refuse409_quandUnCompteActifPorteDejaCetEmail() {
        UUID etablissementCourant = UUID.randomUUID();
        when(utilisateurRepository.existsByEmailAndActifTrue("victime@edukeys.tg")).thenReturn(true);

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            assertThatThrownBy(() -> utilisateurService.creerCompteAvecRoles(
                    "victime@edukeys.tg", "Peu importe", Set.of(RoleCode.GESTIONNAIRE), null))
                    .isInstanceOf(ConflitException.class);
        }

        verify(utilisateurRepository, never()).save(any());
        verify(affectationEtablissementRepository, never()).save(any());
        verify(jetonActivationCompteRepository, never()).save(any());
        verify(generateurMotDePasseTemporaire, never()).generer();
        verify(passwordEncoder, never()).encode(any());
    }

    /**
     * Le message de refus doit être identique, quel que soit le type de
     * compte qui porte déjà l'email — {@code superAdmin} compris — sans quoi
     * l'endpoint deviendrait un oracle permettant à un ADMIN client de
     * détecter l'existence d'un compte de plateforme (ADR-0002 §5). Égalité
     * stricte des messages vérifiée ici : un message spécifique au cas
     * {@code superAdmin} romprait ce test.
     */
    @Test
    void creerCompteAvecRoles_renvoieLeMemeMessage409_quUnCompteOrdinaireOuUnSuperAdminPorteDejaLemail() {
        UUID etablissementCourant = UUID.randomUUID();
        when(utilisateurRepository.existsByEmailAndActifTrue("compte.ordinaire@edukeys.tg")).thenReturn(true);
        when(utilisateurRepository.existsByEmailAndActifTrue("super.admin@edukeys.tg")).thenReturn(true);

        tg.novadigital.edukeys.common.exception.CodeErreur codeCompteOrdinaire;
        tg.novadigital.edukeys.common.exception.CodeErreur codeSuperAdmin;
        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            codeCompteOrdinaire = org.assertj.core.api.Assertions.catchThrowableOfType(
                    () -> utilisateurService.creerCompteAvecRoles(
                            "compte.ordinaire@edukeys.tg", "Peu importe", Set.of(RoleCode.GESTIONNAIRE), null),
                    ConflitException.class).getCode();
            codeSuperAdmin = org.assertj.core.api.Assertions.catchThrowableOfType(
                    () -> utilisateurService.creerCompteAvecRoles(
                            "super.admin@edukeys.tg", "Peu importe", Set.of(RoleCode.GESTIONNAIRE), null),
                    ConflitException.class).getCode();
        }

        assertThat(codeCompteOrdinaire).isNotNull();
        assertThat(codeCompteOrdinaire).isEqualTo(codeSuperAdmin);
    }

    // ------------------------------------------------------------------
    // creerCompteAdministrateurInitial — même comportement depuis
    // l'unification (revue post-implémentation, 3e passe) : l'admin initial
    // d'un établissement neuf doit être un compte réellement créé, jamais
    // rattaché silencieusement à un compte existant.
    // ------------------------------------------------------------------

    @Test
    void creerCompteAdministrateurInitial_refuse409_quandLemailEstDejaPorteParUnCompteActif() {
        UUID etablissementCourant = UUID.randomUUID();
        when(utilisateurRepository.existsByEmailAndActifTrue("admin.client@edukeys.tg")).thenReturn(true);

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            assertThatThrownBy(() -> utilisateurService.creerCompteAdministrateurInitial(
                    "admin.client@edukeys.tg", "Peu importe"))
                    .isInstanceOf(ConflitException.class);
        }

        verify(utilisateurRepository, never()).save(any());
        verify(affectationEtablissementRepository, never()).save(any());
        verify(jetonActivationCompteRepository, never()).save(any());
    }

    @Test
    void creerCompteAdministrateurInitial_creeUnNouveauCompteAdmin_quandEmailInconnu() {
        UUID etablissementCourant = UUID.randomUUID();
        when(utilisateurRepository.findByEmailAndActifTrue("nouvel.admin@edukeys.tg")).thenReturn(Optional.empty());
        when(generateurMotDePasseTemporaire.generer()).thenReturn("MotDePasseAdmin1");
        when(passwordEncoder.encode("MotDePasseAdmin1")).thenReturn("hash-bcrypt");
        when(jetonHacheur.hacher("MotDePasseAdmin1")).thenReturn("hash-sha256");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));
        when(affectationEtablissementRepository.save(any(AffectationEtablissement.class))).thenAnswer(inv -> inv.getArgument(0));

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            UtilisateurService.CompteCree resultat =
                    utilisateurService.creerCompteAdministrateurInitial("nouvel.admin@edukeys.tg", "Nouvel Admin");

            assertThat(resultat.motDePasseTemporaire()).isEqualTo("MotDePasseAdmin1");
            assertThat(resultat.affectation().getRoles()).containsExactly(RoleCode.ADMIN);
        }
    }

    // ------------------------------------------------------------------
    // changerMotDePasseSoiMeme
    // ------------------------------------------------------------------

    @Test
    void changerMotDePasseSoiMeme_refuse_quandLancienMotDePasseEstIncorrect() {
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));
        when(passwordEncoder.matches("mauvais", "hash")).thenReturn(false);

        assertThatThrownBy(() -> utilisateurService.changerMotDePasseSoiMeme(utilisateurId, "mauvais", "Nouveau123!"))
                .isInstanceOf(IdentifiantsInvalidesException.class);

        verify(utilisateurRepository, never()).save(any());
    }

    /** Branche l'expiration du jeton d'activation sur le changement de mot de passe (US-04, revue). */
    @Test
    void changerMotDePasseSoiMeme_refuse_quandLeJetonDActivationActifEstExpire() {
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        utilisateur.exigerChangementMotDePasse();
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));
        when(passwordEncoder.matches("motDePasseTemporaire", "hash")).thenReturn(true);
        when(jetonActivationCompteRepository.existsByUtilisateurIdAndActifTrueAndDateExpirationBefore(eq(utilisateurId), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> utilisateurService.changerMotDePasseSoiMeme(
                utilisateurId, "motDePasseTemporaire", "Nouveau123!"))
                .isInstanceOf(MotDePasseTemporaireExpireException.class);

        // Jamais évalué avant que l'ancien mot de passe se soit révélé correct,
        // et le mot de passe n'est pas modifié quand le jeton est expiré.
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void changerMotDePasseSoiMeme_reussit_quandLeJetonDActivationEstEncoreValide() {
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        utilisateur.exigerChangementMotDePasse();
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));
        when(passwordEncoder.matches("motDePasseTemporaire", "hash")).thenReturn(true);
        when(jetonActivationCompteRepository.existsByUtilisateurIdAndActifTrueAndDateExpirationBefore(eq(utilisateurId), any()))
                .thenReturn(false);
        when(passwordEncoder.encode("Nouveau123!")).thenReturn("nouveau-hash");

        JetonActivationCompte jetonActif = new JetonActivationCompte(utilisateur, "hache", Instant.now().plusSeconds(3600));
        when(jetonActivationCompteRepository.findByUtilisateurIdAndActifTrue(utilisateurId)).thenReturn(List.of(jetonActif));

        JetonRafraichissement refreshActif = new JetonRafraichissement(utilisateur, "hache-refresh", Instant.now().plusSeconds(3600));
        when(jetonRafraichissementRepository.findByUtilisateurIdAndActifTrue(utilisateurId)).thenReturn(List.of(refreshActif));

        utilisateurService.changerMotDePasseSoiMeme(utilisateurId, "motDePasseTemporaire", "Nouveau123!");

        assertThat(utilisateur.getMotDePasseHache()).isEqualTo("nouveau-hash");
        assertThat(utilisateur.isMotDePasseAChanger()).isFalse();
        assertThat(jetonActif.estConsomme()).isTrue();
        assertThat(jetonActif.isActif()).isFalse();
        assertThat(refreshActif.isActif()).isFalse();
        verify(utilisateurRepository).save(utilisateur);
    }

    // ------------------------------------------------------------------
    // regenererMotDePasseTemporaire — défense en profondeur (revue
    // post-implémentation, point A) : referme indépendamment le second bout
    // du chemin d'exploitation en deux appels (rattachement local, puis
    // régénération).
    // ------------------------------------------------------------------

    @Test
    void regenererMotDePasseTemporaire_refuse_quandLeCompteEstAffecteActivementAilleurs() {
        UUID etablissementCourant = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementCourant))
                .thenReturn(true);
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndActifTrueAndEtablissementIdNot(utilisateurId, etablissementCourant))
                .thenReturn(true);

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            assertThatThrownBy(() -> utilisateurService.regenererMotDePasseTemporaire(utilisateurId))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        verify(utilisateurRepository, never()).save(any());
        verify(jetonActivationCompteRepository, never()).save(any());
    }

    @Test
    void regenererMotDePasseTemporaire_reussit_quandLeCompteNestAffecteQueLocalement() {
        UUID etablissementCourant = UUID.randomUUID();
        UUID utilisateurId = UUID.randomUUID();
        Utilisateur utilisateur = new Utilisateur("local@edukeys.tg", "hash", "Local", false);
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementCourant))
                .thenReturn(true);
        when(affectationEtablissementRepository
                .existsByUtilisateurIdAndActifTrueAndEtablissementIdNot(utilisateurId, etablissementCourant))
                .thenReturn(false);
        when(utilisateurRepository.findById(utilisateurId)).thenReturn(Optional.of(utilisateur));
        when(generateurMotDePasseTemporaire.generer()).thenReturn("NouveauTmp1");
        when(passwordEncoder.encode("NouveauTmp1")).thenReturn("nouveau-hash");
        when(jetonHacheur.hacher("NouveauTmp1")).thenReturn("nouveau-hash-sha");

        String motDePasseTemporaire;
        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            motDePasseTemporaire = utilisateurService.regenererMotDePasseTemporaire(utilisateurId);
        }

        assertThat(motDePasseTemporaire).isEqualTo("NouveauTmp1");
        assertThat(utilisateur.getMotDePasseHache()).isEqualTo("nouveau-hash");
        assertThat(utilisateur.isMotDePasseAChanger()).isTrue();
        verify(jetonActivationCompteRepository).save(any(JetonActivationCompte.class));
    }

    // ------------------------------------------------------------------
    // desactiverDansEtablissementCourant — garde "pas de dernier ADMIN actif"
    // (revue post-implémentation, point 5). Inatteignable par HTTP dans
    // l'état actuel du RBAC (seul ADMIN porte UTILISATEUR_GERER, donc
    // l'appelant HTTP est toujours lui-même un ADMIN actif distinct de la
    // cible) : testée ici directement au niveau service, avec un appelant
    // forgé distinct de la cible.
    // ------------------------------------------------------------------

    @Test
    void desactiverDansEtablissementCourant_refuse_quandAucunAutreAdminActif() {
        UUID etablissementCourant = UUID.randomUUID();
        UUID cibleId = UUID.randomUUID();
        UUID appelantId = UUID.randomUUID();
        Utilisateur cible = new Utilisateur("dernier.admin@edukeys.tg", "hash", "Dernier Admin", false);
        AffectationEtablissement affectation =
                new AffectationEtablissement(cible, etablissementCourant, EnumSet.of(RoleCode.ADMIN));

        when(affectationEtablissementRepository
                .findByUtilisateurIdAndEtablissementIdAndActifTrue(cibleId, etablissementCourant))
                .thenReturn(Optional.of(affectation));
        when(affectationEtablissementRepository.compterAutresAdminsActifs(etablissementCourant, affectation.getId()))
                .thenReturn(0L);

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            assertThatThrownBy(() -> utilisateurService.desactiverDansEtablissementCourant(cibleId, appelantId))
                    .isInstanceOf(RegleMetierViolee.class);
        }

        assertThat(affectation.isActif()).isTrue();
        verify(affectationEtablissementRepository, never()).save(any());
    }

    @Test
    void desactiverDansEtablissementCourant_autorise_quandUnAutreAdminActifSubsiste() {
        UUID etablissementCourant = UUID.randomUUID();
        UUID cibleId = UUID.randomUUID();
        UUID appelantId = UUID.randomUUID();
        Utilisateur cible = new Utilisateur("admin.remplace@edukeys.tg", "hash", "Admin Remplacé", false);
        AffectationEtablissement affectation =
                new AffectationEtablissement(cible, etablissementCourant, EnumSet.of(RoleCode.ADMIN));

        when(affectationEtablissementRepository
                .findByUtilisateurIdAndEtablissementIdAndActifTrue(cibleId, etablissementCourant))
                .thenReturn(Optional.of(affectation));
        when(affectationEtablissementRepository.compterAutresAdminsActifs(etablissementCourant, affectation.getId()))
                .thenReturn(1L);
        when(affectationEtablissementRepository.existsByUtilisateurIdAndActifTrueAndIdNot(cible.getId(), affectation.getId()))
                .thenReturn(true);

        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            utilisateurService.desactiverDansEtablissementCourant(cibleId, appelantId);
        }

        assertThat(affectation.isActif()).isFalse();
        verify(affectationEtablissementRepository).save(affectation);
        // Une autre affectation active du même compte subsiste : le compte
        // Utilisateur lui-même ne doit pas être désactivé.
        verify(utilisateurRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // US-08a : identifiant de connexion, rôles non attribuables, expiration explicite
    // ------------------------------------------------------------------

    @Test
    void creerCompteAvecRoles_pose_identifiantDeConnexionEgalALEmailNormalise() {
        when(generateurMotDePasseTemporaire.generer()).thenReturn("Temp-123");
        when(passwordEncoder.encode(any())).thenReturn("hache");
        when(jetonHacheur.hacher(any())).thenReturn("sha");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));
        when(affectationEtablissementRepository.save(any(AffectationEtablissement.class))).thenAnswer(i -> i.getArgument(0));

        try (var portee = ContexteEtablissement.ouvrir(UUID.randomUUID())) {
            var compte = utilisateurService.creerCompteAvecRoles(
                    "Marie.Dupont@Edukeys.TG", "Marie", Set.of(RoleCode.GESTIONNAIRE), null);
            assertThat(compte.utilisateur().getEmail()).isEqualTo("marie.dupont@edukeys.tg");
            assertThat(compte.utilisateur().getIdentifiantConnexion()).isEqualTo("marie.dupont@edukeys.tg");
        }
    }

    @Test
    void creerCompteAvecRoles_refuse409_quandLEmailEstDejaUnIdentifiantActif() {
        // Un matricule ne ressemble pas à un email, mais un identifiant actif identique doit bloquer.
        when(utilisateurRepository.existsByIdentifiantConnexionAndActifTrue("pris@edukeys.tg")).thenReturn(true);

        try (var portee = ContexteEtablissement.ouvrir(UUID.randomUUID())) {
            assertThatThrownBy(() -> utilisateurService.creerCompteAvecRoles(
                    "pris@edukeys.tg", "X", Set.of(RoleCode.GESTIONNAIRE), null))
                    .isInstanceOf(ConflitException.class);
        }
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void creerCompteAvecRoles_refuseEleveEtParent_avecUnCodeExplicite() {
        try (var portee = ContexteEtablissement.ouvrir(UUID.randomUUID())) {
            for (RoleCode interdit : new RoleCode[] {RoleCode.ELEVE, RoleCode.PARENT}) {
                assertThatThrownBy(() -> utilisateurService.creerCompteAvecRoles(
                        "x@edukeys.tg", "X", EnumSet.of(RoleCode.GESTIONNAIRE, interdit), null))
                        .isInstanceOf(RegleMetierViolee.class)
                        .extracting(e -> ((RegleMetierViolee) e).getCode())
                        .isEqualTo(tg.novadigital.edukeys.common.exception.CodeErreur.ROLE_NON_ATTRIBUABLE_MANUELLEMENT);
            }
        }
        verify(utilisateurRepository, never()).save(any());
    }

    private AffectationEtablissement affectationExistante(UUID etablissementId, UUID utilisateurId, Utilisateur compte, RoleCode... roles) {
        AffectationEtablissement affectation = new AffectationEtablissement(compte, etablissementId, EnumSet.copyOf(Set.of(roles)));
        when(affectationEtablissementRepository.findByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId))
                .thenReturn(Optional.of(affectation));
        return affectation;
    }

    @Test
    void remplacerRoles_refuseEleveEtParentAjoutes() {
        UUID etab = UUID.randomUUID();
        UUID cible = UUID.randomUUID();
        affectationExistante(etab, cible, new Utilisateur("p@edukeys.tg", "h", "P", false), RoleCode.GESTIONNAIRE);
        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            for (RoleCode interdit : new RoleCode[] {RoleCode.ELEVE, RoleCode.PARENT}) {
                assertThatThrownBy(() -> utilisateurService.remplacerRoles(
                        cible, EnumSet.of(RoleCode.GESTIONNAIRE, interdit), UUID.randomUUID()))
                        .isInstanceOf(RegleMetierViolee.class)
                        .extracting(e -> ((RegleMetierViolee) e).getCode())
                        .isEqualTo(tg.novadigital.edukeys.common.exception.CodeErreur.ROLE_NON_ATTRIBUABLE_MANUELLEMENT);
            }
        }
        verify(affectationEtablissementRepository, never()).save(any());
    }

    @Test
    void remplacerRoles_accepteLeRenvoiTelQuelDunCompteEnseignantParent() {
        UUID etab = UUID.randomUUID();
        UUID cible = UUID.randomUUID();
        AffectationEtablissement affectation = affectationExistante(
                etab, cible, new Utilisateur("ep@edukeys.tg", "h", "EP", false), RoleCode.ENSEIGNANT, RoleCode.PARENT);

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            utilisateurService.remplacerRoles(cible, EnumSet.of(RoleCode.ENSEIGNANT, RoleCode.PARENT), UUID.randomUUID());
        }
        assertThat(affectation.getRoles()).containsExactlyInAnyOrder(RoleCode.ENSEIGNANT, RoleCode.PARENT);
    }

    @Test
    void remplacerRoles_conserveParentDejaPresent_quandSeulEnseignantEstEnvoye() {
        UUID etab = UUID.randomUUID();
        UUID cible = UUID.randomUUID();
        AffectationEtablissement affectation = affectationExistante(
                etab, cible, new Utilisateur("ep@edukeys.tg", "h", "EP", false), RoleCode.ENSEIGNANT, RoleCode.PARENT);

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            utilisateurService.remplacerRoles(cible, EnumSet.of(RoleCode.ENSEIGNANT), UUID.randomUUID());
        }
        assertThat(affectation.getRoles()).containsExactlyInAnyOrder(RoleCode.ENSEIGNANT, RoleCode.PARENT);
    }

    @Test
    void remplacerRoles_refuseLAjoutDunRoleDuPersonnelSurUnCompteEleve() {
        UUID etab = UUID.randomUUID();
        UUID cible = UUID.randomUUID();
        AffectationEtablissement affectation = affectationExistante(
                etab, cible, new Utilisateur(null, "mat-1", "h", "Élève", false), RoleCode.ELEVE);

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            assertThatThrownBy(() -> utilisateurService.remplacerRoles(
                    cible, EnumSet.of(RoleCode.ELEVE, RoleCode.GESTIONNAIRE), UUID.randomUUID()))
                    .isInstanceOf(RegleMetierViolee.class);
            // Renvoyer ELEVE seul reste un no-op légitime.
            utilisateurService.remplacerRoles(cible, EnumSet.of(RoleCode.ELEVE), UUID.randomUUID());
        }
        assertThat(affectation.getRoles()).containsExactly(RoleCode.ELEVE);
    }

    // --- emettreMotDePasseTemporaireAvecExpiration : mêmes bornes que regenererMotDePasseTemporaire

    private Utilisateur eleveDansEtablissement(UUID etab, UUID id) {
        Utilisateur eleve = new Utilisateur(null, "mat-2026-0001", "hash", "Élève", false);
        affectationExistante(etab, id, eleve, RoleCode.ELEVE);
        when(generateurMotDePasseTemporaire.generer()).thenReturn("Temp-123");
        when(passwordEncoder.encode("Temp-123")).thenReturn("hache");
        when(jetonHacheur.hacher("Temp-123")).thenReturn("sha");
        return eleve;
    }

    @Test
    void emettreMotDePasseTemporaireAvecExpiration_utiliseLaDateExplicite() {
        UUID etab = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Utilisateur eleve = eleveDansEtablissement(etab, id);
        Instant expiration = Instant.now().plus(java.time.Duration.ofDays(45));

        String motDePasse;
        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            motDePasse = utilisateurService.emettreMotDePasseTemporaireAvecExpiration(id, expiration);
        }

        assertThat(motDePasse).isEqualTo("Temp-123");
        assertThat(eleve.isMotDePasseAChanger()).isTrue();
        org.mockito.ArgumentCaptor<JetonActivationCompte> jeton = org.mockito.ArgumentCaptor.forClass(JetonActivationCompte.class);
        verify(jetonActivationCompteRepository).save(jeton.capture());
        assertThat(jeton.getValue().getDateExpiration()).isEqualTo(expiration);
    }

    @Test
    void emettreMotDePasseTemporaireAvecExpiration_leveIllegalArgument_pourDateNulleOuDansLePasse() {
        UUID etab = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        eleveDansEtablissement(etab, id);
        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(id, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(id, Instant.now().minusSeconds(5)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(jetonActivationCompteRepository, never()).save(any());
    }

    /** US-08, C1 : le dépassement du maximum est une règle métier (422), jamais une IllegalArgumentException (500). */
    @Test
    void emettreMotDePasseTemporaireAvecExpiration_leveRegleMetier_auDelaDuMaximum_sansRienEcrire() {
        UUID etab = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        eleveDansEtablissement(etab, id);
        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(
                    id, Instant.now().plus(java.time.Duration.ofDays(241))))
                    .isInstanceOf(tg.novadigital.edukeys.common.exception.RegleMetierViolee.class)
                    .extracting(e -> ((tg.novadigital.edukeys.common.exception.RegleMetierViolee) e).getCode())
                    .isEqualTo(tg.novadigital.edukeys.common.exception.CodeErreur.MOT_DE_PASSE_TEMPORAIRE_EXPIRATION_HORS_BORNES);
            // Juste en dessous du maximum : accepté tel quel, jamais ramené en arrière.
            assertThat(utilisateurService.emettreMotDePasseTemporaireAvecExpiration(
                    id, Instant.now().plus(java.time.Duration.ofDays(239)))).isNotBlank();
        }
    }

    /** L'émetteur compare l'expiration à l'horloge injectée, pas à l'heure système. */
    @Test
    void emettreMotDePasseTemporaireAvecExpiration_utiliseLHorlogeInjectee() {
        java.time.Clock fixe = java.time.Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);
        UtilisateurService service = new UtilisateurService(
                utilisateurRepository, jetonRafraichissementRepository, affectationEtablissementRepository,
                jetonActivationCompteRepository, passwordEncoder, jetonHacheur, generateurMotDePasseTemporaire,
                java.time.Duration.ofDays(14), java.time.Duration.ofDays(240), fixe);
        UUID etab = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        eleveDansEtablissement(etab, id);
        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            // Passé pour l'heure système, futur pour l'horloge injectée : accepté.
            assertThat(service.emettreMotDePasseTemporaireAvecExpiration(id, Instant.parse("2020-01-10T00:00:00Z"))).isNotBlank();
            // Futur proche pour l'heure système mais > 240 j après l'horloge injectée : refusé.
            assertThatThrownBy(() -> service.emettreMotDePasseTemporaireAvecExpiration(id, Instant.parse("2020-12-01T00:00:00Z")))
                    .isInstanceOf(tg.novadigital.edukeys.common.exception.RegleMetierViolee.class);
        }
    }

    @Test
    void emettreMotDePasseTemporaireAvecExpiration_refuseSansContexteEtablissement() {
        assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(
                UUID.randomUUID(), Instant.now().plus(java.time.Duration.ofDays(5))))
                .isInstanceOf(ContexteEtablissementAbsentException.class);
    }

    @Test
    void emettreMotDePasseTemporaireAvecExpiration_refuseUnCompteNonAffecteIci() {
        UUID etab = UUID.randomUUID();
        when(affectationEtablissementRepository.findByUtilisateurIdAndEtablissementIdAndActifTrue(any(), eq(etab)))
                .thenReturn(Optional.empty());
        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(
                    UUID.randomUUID(), Instant.now().plus(java.time.Duration.ofDays(5))))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void emettreMotDePasseTemporaireAvecExpiration_refuseUnCompteAffecteAilleurs_unSuperAdmin_unInactif_ouUnCompteDuPersonnel() {
        UUID etab = UUID.randomUUID();
        Instant expiration = Instant.now().plus(java.time.Duration.ofDays(5));

        UUID ailleurs = UUID.randomUUID();
        eleveDansEtablissement(etab, ailleurs);
        when(affectationEtablissementRepository.existsByUtilisateurIdAndActifTrueAndEtablissementIdNot(ailleurs, etab)).thenReturn(true);

        UUID superAdmin = UUID.randomUUID();
        affectationExistante(etab, superAdmin, new Utilisateur("sa@edukeys.tg", "h", "SA", true), RoleCode.ELEVE);

        UUID inactif = UUID.randomUUID();
        Utilisateur compteInactif = new Utilisateur(null, "mat-inactif", "h", "Inactif", false);
        compteInactif.desactiver();
        affectationExistante(etab, inactif, compteInactif, RoleCode.ELEVE);

        UUID personnel = UUID.randomUUID();
        affectationExistante(etab, personnel, new Utilisateur("pers@edukeys.tg", "h", "Pers", false), RoleCode.ENSEIGNANT);

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            for (UUID refuse : new UUID[] {ailleurs, superAdmin, inactif, personnel}) {
                assertThatThrownBy(() -> utilisateurService.emettreMotDePasseTemporaireAvecExpiration(refuse, expiration))
                        .isInstanceOf(RessourceIntrouvableException.class);
            }
        }
        verify(jetonActivationCompteRepository, never()).save(any());
    }

    // --- creerCompteEleve (US-08)

    @Test
    void creerCompteEleve_creeUnCompteNeuf_identifiantNormalise_roleEleveSeul_sansJeton() {
        UUID etab = UUID.randomUUID();
        when(generateurMotDePasseTemporaire.generer()).thenReturn("Secret-jamais-rendu");
        when(passwordEncoder.encode("Secret-jamais-rendu")).thenReturn("hache");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));
        org.mockito.ArgumentCaptor<AffectationEtablissement> affectation = org.mockito.ArgumentCaptor.forClass(AffectationEtablissement.class);
        org.mockito.ArgumentCaptor<Utilisateur> compte = org.mockito.ArgumentCaptor.forClass(Utilisateur.class);

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            utilisateurService.creerCompteEleve("CSJ-2026-00001", "KODJO Ama");
        }

        verify(utilisateurRepository).save(compte.capture());
        assertThat(compte.getValue().getIdentifiantConnexion()).isEqualTo("csj-2026-00001");
        assertThat(compte.getValue().getEmail()).isNull();
        assertThat(compte.getValue().isMotDePasseAChanger()).isTrue();
        assertThat(compte.getValue().getMotDePasseHache()).isEqualTo("hache");
        verify(affectationEtablissementRepository).save(affectation.capture());
        assertThat(affectation.getValue().getRoles()).containsExactly(RoleCode.ELEVE);
        assertThat(affectation.getValue().getSiteId()).isNull();
        assertThat(affectation.getValue().getEtablissementId()).isEqualTo(etab);
        verify(jetonActivationCompteRepository, never()).save(any());
    }

    @Test
    void creerCompteEleve_refuseUnIdentifiantDejaPorte_sansRienEcrire() {
        UUID etab = UUID.randomUUID();
        when(utilisateurRepository.existsByIdentifiantConnexionAndActifTrue("csj-2026-00001")).thenReturn(true);

        try (var portee = ContexteEtablissement.ouvrir(etab)) {
            assertThatThrownBy(() -> utilisateurService.creerCompteEleve("CSJ-2026-00001", "KODJO Ama"))
                    .isInstanceOfSatisfying(ConflitException.class,
                            e -> assertThat(e.getCode()).isEqualTo(tg.novadigital.edukeys.common.exception.CodeErreur.UTILISATEUR_IDENTIFIANT_DUPLIQUE));
        }
        verify(utilisateurRepository, never()).save(any());
        verify(affectationEtablissementRepository, never()).save(any());
    }
}
