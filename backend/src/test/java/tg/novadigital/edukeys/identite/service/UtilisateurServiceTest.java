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
                java.time.Duration.ofDays(14));
    }


    @Test
    void leveUneExceptionRessourceIntrouvable_quandUtilisateurInexistant() {
        UUID id = UUID.randomUUID();
        when(utilisateurRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> utilisateurService.desactiverCompte(id))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    void desactiveLeCompteEtRevoqueSesJetonsActifs_quandDesactivationDemandee() {
        Utilisateur utilisateur = new Utilisateur("marie@edukeys.tg", "hash", "Marie Dupont", false);
        UUID id = UUID.randomUUID();
        when(utilisateurRepository.findById(id)).thenReturn(Optional.of(utilisateur));

        JetonRafraichissement jeton1 = new JetonRafraichissement(utilisateur, "h1", Instant.now().plusSeconds(3600));
        JetonRafraichissement jeton2 = new JetonRafraichissement(utilisateur, "h2", Instant.now().plusSeconds(3600));
        when(jetonRafraichissementRepository.findByUtilisateurIdAndActifTrue(id)).thenReturn(List.of(jeton1, jeton2));

        utilisateurService.desactiverCompte(id);

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

        String messageCompteOrdinaire;
        String messageSuperAdmin;
        try (var portee = ContexteEtablissement.ouvrir(etablissementCourant)) {
            messageCompteOrdinaire = org.assertj.core.api.Assertions.catchThrowableOfType(
                    () -> utilisateurService.creerCompteAvecRoles(
                            "compte.ordinaire@edukeys.tg", "Peu importe", Set.of(RoleCode.GESTIONNAIRE), null),
                    ConflitException.class).getMessage();
            messageSuperAdmin = org.assertj.core.api.Assertions.catchThrowableOfType(
                    () -> utilisateurService.creerCompteAvecRoles(
                            "super.admin@edukeys.tg", "Peu importe", Set.of(RoleCode.GESTIONNAIRE), null),
                    ConflitException.class).getMessage();
        }

        assertThat(messageCompteOrdinaire).isNotBlank();
        assertThat(messageCompteOrdinaire).isEqualTo(messageSuperAdmin);
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
}
