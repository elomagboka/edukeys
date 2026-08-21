package tg.novadigital.edukeys.identite.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.common.exception.AccesInterditException;
import tg.novadigital.edukeys.common.exception.IdentifiantsInvalidesException;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.securite.JournalSecurite;
import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;
import tg.novadigital.edukeys.identite.domain.JetonRafraichissement;
import tg.novadigital.edukeys.identite.domain.RoleCode;
import tg.novadigital.edukeys.identite.domain.Utilisateur;
import tg.novadigital.edukeys.identite.repository.AffectationEtablissementRepository;
import tg.novadigital.edukeys.identite.repository.JetonRafraichissementRepository;
import tg.novadigital.edukeys.identite.repository.UtilisateurRepository;
import tg.novadigital.edukeys.identite.security.JwtService;
import tg.novadigital.edukeys.identite.web.JetonsReponseDto;

/**
 * Authentification : connexion, rafraîchissement de jeton et bascule
 * d'établissement actif. Toute la logique métier (choix de l'établissement
 * actif, union des rôles cumulés, rotation du refresh token) vit ici, jamais
 * dans le contrôleur.
 */
@Service
public class AuthService {

    /**
     * Alignée sur l'analyse de risque de l'issue de stockage des jetons : un
     * jeton volé ne doit pas donner plus d'une semaine d'accès (correction
     * T-04, lot 2 n°9 — le code portait 30 jours, l'analyse en supposait 7).
     */
    public static final Duration DUREE_REFRESH_TOKEN = Duration.ofDays(7);

    /**
     * Hash BCrypt d'une valeur arbitraire, jamais le mot de passe d'un compte
     * réel : sert uniquement à payer le même coût de calcul qu'une vérification
     * réelle quand l'email est inconnu, pour qu'un attaquant ne puisse pas
     * distinguer un compte inexistant d'un mauvais mot de passe par la durée
     * de réponse (correction T-04, lot 2 n°6).
     */
    private static final String HACHE_FACTICE =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5L5eLKKF.p4d1IyyMdCV0lJd5v9lC";

    private final UtilisateurRepository utilisateurRepository;
    private final AffectationEtablissementRepository affectationEtablissementRepository;
    private final JetonRafraichissementRepository jetonRafraichissementRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JetonHacheur jetonHacheur;

    public AuthService(
            UtilisateurRepository utilisateurRepository,
            AffectationEtablissementRepository affectationEtablissementRepository,
            JetonRafraichissementRepository jetonRafraichissementRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JetonHacheur jetonHacheur) {
        this.utilisateurRepository = utilisateurRepository;
        this.affectationEtablissementRepository = affectationEtablissementRepository;
        this.jetonRafraichissementRepository = jetonRafraichissementRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jetonHacheur = jetonHacheur;
    }

    @Transactional
    public JetonsReponseDto connecter(String email, String motDePasseEnClair, String adresseIp) {
        Utilisateur utilisateur = utilisateurRepository.findByEmailAndActifTrue(email).orElse(null);

        if (utilisateur == null) {
            // Toujours payer le coût BCrypt, même sans compte à comparer : sinon
            // l'absence de calcul distingue un email inconnu par le temps de
            // réponse (correction T-04, lot 2 n°6).
            passwordEncoder.matches(motDePasseEnClair, HACHE_FACTICE);
            JournalSecurite.echecAuthentificationCompteInconnu(email, adresseIp);
            throw new IdentifiantsInvalidesException("Email ou mot de passe incorrect.");
        }

        if (!passwordEncoder.matches(motDePasseEnClair, utilisateur.getMotDePasseHache())) {
            JournalSecurite.echecAuthentificationCompteExistant(
                    utilisateur.getId(), adresseIp, "mot_de_passe_incorrect");
            throw new IdentifiantsInvalidesException("Email ou mot de passe incorrect.");
        }

        return emettreJetons(utilisateur);
    }

    @Transactional
    public JetonsReponseDto rafraichir(String refreshTokenEnClair) {
        String hache = jetonHacheur.hacher(refreshTokenEnClair);
        JetonRafraichissement jeton = jetonRafraichissementRepository.findByJetonHache(hache)
                .orElseThrow(() -> new IdentifiantsInvalidesException("Jeton de rafraîchissement invalide."));

        if (!jeton.isActif()) {
            // Rejeu d'un jeton déjà tourné (ou déjà révoqué) : signal classique d'un
            // vol de jeton. On coupe toute la chaîne, pas seulement celui-ci.
            revoquerFamille(jeton.getFamilleId());
            throw new IdentifiantsInvalidesException("Jeton de rafraîchissement invalide.");
        }

        if (jeton.estExpire()) {
            throw new IdentifiantsInvalidesException("Jeton de rafraîchissement expiré ou révoqué.");
        }

        Utilisateur utilisateur = jeton.getUtilisateur();
        if (!utilisateur.isActif()) {
            throw new AccesInterditException("Compte désactivé.");
        }

        // Rotation : le jeton présenté est révoqué, un nouveau est émis dans la
        // même famille, en conservant l'établissement actif de ce jeton (une
        // bascule d'établissement ne doit pas être oubliée au prochain refresh).
        jeton.desactiver();
        jetonRafraichissementRepository.save(jeton);

        return emettreJetons(utilisateur, jeton.getFamilleId(), jeton.getEtablissementActifId());
    }

    /**
     * Change l'établissement actif du compte authentifié : émet une nouvelle
     * paire de jetons (nouvelle famille) portant l'établissement demandé,
     * après vérification que l'utilisateur y est bien affecté (correction
     * T-04, lot 2 n°8 — au périmètre initial de la spec T-04).
     */
    @Transactional
    public JetonsReponseDto basculerEtablissement(UUID utilisateurId, UUID etablissementCibleId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        if (!utilisateur.isActif()) {
            throw new AccesInterditException("Compte désactivé.");
        }

        boolean autorise = utilisateur.isSuperAdmin()
                || affectationEtablissementRepository.existsByUtilisateurIdAndEtablissementIdAndActifTrue(
                        utilisateurId, etablissementCibleId);
        if (!autorise) {
            throw new AccesInterditException("Aucune affectation active sur cet établissement.");
        }

        return emettreJetons(utilisateur, UUID.randomUUID(), etablissementCibleId);
    }

    private void revoquerFamille(UUID familleId) {
        jetonRafraichissementRepository.findByFamilleIdAndActifTrue(familleId)
                .forEach(actif -> {
                    actif.desactiver();
                    jetonRafraichissementRepository.save(actif);
                });
    }

    private JetonsReponseDto emettreJetons(Utilisateur utilisateur) {
        return emettreJetons(utilisateur, UUID.randomUUID(), null);
    }

    /**
     * @param etablissementSouhaiteId établissement à activer si l'utilisateur y
     *                                est affecté ({@code null} : premier
     *                                établissement affecté, comportement de
     *                                login initial). Une affectation révoquée
     *                                entre-temps (bascule ou refresh) dégrade
     *                                silencieusement vers aucun établissement
     *                                actif plutôt que d'échouer, sauf pour
     *                                SUPER_ADMIN qui n'a besoin d'aucune
     *                                affectation pour activer un établissement.
     */
    private JetonsReponseDto emettreJetons(Utilisateur utilisateur, UUID familleId, UUID etablissementSouhaiteId) {
        List<AffectationEtablissement> affectations =
                affectationEtablissementRepository.findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(utilisateur.getId());

        UUID etablissementActif = null;
        Set<String> codesRolesMutable = new HashSet<>();

        // SUPER_ADMIN est hors périmètre établissement (docs/adr/0002-multi-etablissement.md) :
        // le rôle n'est porté par aucune affectation, il doit être ajouté indépendamment.
        if (utilisateur.isSuperAdmin()) {
            codesRolesMutable.add(RoleCode.SUPER_ADMIN.name());
        }

        AffectationEtablissement affectationActive = trouverAffectationActive(affectations, etablissementSouhaiteId);
        if (affectationActive != null) {
            etablissementActif = affectationActive.getEtablissementId();
            affectationActive.getRoles().forEach(role -> codesRolesMutable.add(role.name()));
        } else if (etablissementSouhaiteId != null && utilisateur.isSuperAdmin()) {
            etablissementActif = etablissementSouhaiteId;
        }

        Set<String> codesRoles = Set.copyOf(codesRolesMutable);

        String accessToken = jwtService.genererAccessToken(utilisateur.getId(), etablissementActif, codesRoles);

        String refreshTokenEnClair = jetonHacheur.genererJetonEnClair();
        JetonRafraichissement jetonRafraichissement = new JetonRafraichissement(
                utilisateur,
                jetonHacheur.hacher(refreshTokenEnClair),
                Instant.now().plus(DUREE_REFRESH_TOKEN),
                familleId,
                etablissementActif);
        jetonRafraichissementRepository.save(jetonRafraichissement);

        return new JetonsReponseDto(
                accessToken,
                refreshTokenEnClair,
                JwtService.DUREE_ACCESS_TOKEN.toSeconds(),
                etablissementActif,
                codesRoles.stream().sorted().toList());
    }

    private AffectationEtablissement trouverAffectationActive(
            List<AffectationEtablissement> affectations, UUID etablissementSouhaiteId) {
        if (etablissementSouhaiteId != null) {
            return affectations.stream()
                    .filter(affectation -> etablissementSouhaiteId.equals(affectation.getEtablissementId()))
                    .findFirst()
                    .orElse(null);
        }
        return affectations.isEmpty() ? null : affectations.get(0);
    }
}
