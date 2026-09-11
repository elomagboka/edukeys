package tg.novadigital.edukeys.identite.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tg.novadigital.edukeys.common.exception.ConflitException;
import tg.novadigital.edukeys.common.exception.IdentifiantsInvalidesException;
import tg.novadigital.edukeys.common.exception.MotDePasseTemporaireExpireException;
import tg.novadigital.edukeys.common.exception.RegleMetierViolee;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
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

/**
 * Gestion du cycle de vie du compte utilisateur et des affectations RBAC
 * (US-04). La désactivation d'un compte révoque immédiatement tous ses
 * refresh tokens actifs, effet de bord exigé par l'arbitrage T-04 n°3 : un
 * compte désactivé ne doit pas pouvoir se reconnecter via un jeton émis avant
 * sa désactivation.
 *
 * <p><strong>Point à documenter, pas à corriger dans cette US</strong> (revue
 * post-implémentation) : l'index d'unicité sur {@code utilisateurs.email} est
 * un index partiel {@code WHERE actif = TRUE} (CLAUDE.md, règle 4) — un email
 * libéré par désactivation redevient donc immédiatement utilisable pour un
 * <strong>nouveau</strong> compte ({@link #creerCompteAvecRoles} en crée
 * effectivement un nouveau dans ce cas, voir le test correspondant). La
 * conséquence n'est pas corrigée ici : un compte désactivé dont l'email a été
 * repris par un nouveau compte actif devient <strong>définitivement non
 * réactivable</strong> par cet email (toute tentative future bute sur le
 * nouveau titulaire actif). Pour un compte {@code superAdmin} désactivé,
 * c'est une perte de compte de plateforme qu'un simple ADMIN client peut
 * provoquer en créant, dans son propre établissement, un compte avec le même
 * email — sans jamais accéder au compte de plateforme lui-même, ni même
 * apprendre qu'il existe (voir {@link #creerCompteAvecRoles}). Aucune
 * réparation n'existe aujourd'hui pour ce cas ; un futur mécanisme de reprise
 * (SUPER_ADMIN plateforme, hors périmètre établissement) devra probablement
 * le couvrir.</p>
 */
@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final JetonRafraichissementRepository jetonRafraichissementRepository;
    private final AffectationEtablissementRepository affectationEtablissementRepository;
    private final JetonActivationCompteRepository jetonActivationCompteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JetonHacheur jetonHacheur;
    private final GenerateurMotDePasseTemporaire generateurMotDePasseTemporaire;

    /**
     * Durée de validité d'un mot de passe temporaire (US-04), externalisée
     * (jamais en dur) : au-delà, {@code /auth/login} et
     * {@link #changerMotDePasseSoiMeme} refusent avec
     * {@code MotDePasseTemporaireExpireException}. Un jeton d'activation
     * compte plus longtemps qu'un refresh token : le mot de passe temporaire
     * est destiné à être recopié à la main, pas échangé immédiatement contre
     * une session.
     */
    private final Duration dureeValiditeMotDePasseTemporaire;

    public UtilisateurService(
            UtilisateurRepository utilisateurRepository,
            JetonRafraichissementRepository jetonRafraichissementRepository,
            AffectationEtablissementRepository affectationEtablissementRepository,
            JetonActivationCompteRepository jetonActivationCompteRepository,
            PasswordEncoder passwordEncoder,
            JetonHacheur jetonHacheur,
            GenerateurMotDePasseTemporaire generateurMotDePasseTemporaire,
            @Value("${edukeys.securite.mot-de-passe-temporaire.duree-validite:14d}") Duration dureeValiditeMotDePasseTemporaire) {
        this.utilisateurRepository = utilisateurRepository;
        this.jetonRafraichissementRepository = jetonRafraichissementRepository;
        this.affectationEtablissementRepository = affectationEtablissementRepository;
        this.jetonActivationCompteRepository = jetonActivationCompteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jetonHacheur = jetonHacheur;
        this.generateurMotDePasseTemporaire = generateurMotDePasseTemporaire;
        this.dureeValiditeMotDePasseTemporaire = dureeValiditeMotDePasseTemporaire;
    }

    /**
     * <b>Le seul compte que cette méthode peut jamais retourner est celui de
     * l'appelant authentifié lui-même.</b> {@link Utilisateur} n'étend pas
     * {@code EntiteEtablissement} (ADR-0002) : aucun filtre Hibernate ne borne
     * la lecture d'un compte par identifiant. Plutôt que de documenter une
     * restriction d'usage sur une méthode qui accepterait n'importe quel
     * {@code UUID} (piège : un futur {@code GET /utilisateurs/{id}} appellerait
     * naturellement une méthode nommée {@code obtenir(id)} et exposerait tous
     * les comptes de la plateforme), la signature elle-même ne permet plus de
     * demander un autre compte que le sien — l'identifiant recherché est lu
     * directement sur le principal, jamais reçu en paramètre. Pour un accès
     * administratif à un compte précis, borné à l'établissement courant, voir
     * {@link #obtenirDansEtablissementCourant(UUID)}.
     */
    public Utilisateur obtenirSoiMeme(UtilisateurPrincipal principal) {
        return utilisateurRepository.findById(principal.utilisateurId())
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    /**
     * Compte d'un établissement précis, réservé aux appelants portant
     * {@code UTILISATEUR_GERER} ou {@code UTILISATEUR_CONSULTER} (cadré par
     * son propre établissement, ADR-0002 §5) : un ADMIN de l'établissement A
     * ne doit jamais pouvoir atteindre un compte dont la seule affectation
     * est sur B, y compris par accès direct à l'identifiant. {@code Utilisateur}
     * n'étant pas filtré (ADR-0002), le cloisonnement est vérifié ici,
     * explicitement, via
     * {@link AffectationEtablissementRepository#existsByUtilisateurIdAndEtablissementIdAndActifTrue}
     * — jamais déduit d'un simple {@code findById}.
     *
     * <p>Message d'erreur identique à un identifiant réellement inexistant :
     * un ADMIN de A ne doit pas pouvoir distinguer « ce compte n'existe pas »
     * de « ce compte existe, mais pas chez vous » (l'un fuiterait déjà
     * l'existence d'un compte dans un autre établissement).</p>
     */
    public Utilisateur obtenirDansEtablissementCourant(UUID utilisateurId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        boolean affecteAEtablissementCourant = affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId);
        if (!affecteAEtablissementCourant) {
            throw new RessourceIntrouvableException("Utilisateur introuvable.");
        }
        return utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    /**
     * Affectation (avec son compte et ses rôles déjà chargés) d'un compte de
     * l'établissement courant, pour {@code GET /api/v1/utilisateurs/mon-etablissement/{id}}.
     * Message d'erreur identique à un identifiant inexistant, même raison que
     * {@link #obtenirDansEtablissementCourant(UUID)}.
     */
    public AffectationEtablissement obtenirAffectationDansEtablissementCourant(UUID utilisateurId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return affectationEtablissementRepository
                .findByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));
    }

    /**
     * Comptes de tous les établissements, sans distinction : {@link Utilisateur}
     * n'est délibérément pas rattaché à un établissement (voir sa javadoc et
     * ADR-0002) — un même compte peut porter plusieurs {@code AffectationEtablissement}
     * sur des établissements différents, donc « son » établissement n'existe
     * pas. Cet endpoint est réservé à SUPER_ADMIN (permission
     * {@code UTILISATEUR_GERER_PLATEFORME}), jamais à ADMIN.
     */
    public Page<Utilisateur> listerTous(Pageable pageable) {
        return utilisateurRepository.findAll(pageable);
    }

    /**
     * Comptes actifs de l'établissement courant (contexte ouvert par
     * {@code ContexteEtablissementFilter} depuis le JWT, ADR-0002), via une
     * affectation elle-même active. Réservé à {@code UTILISATEUR_GERER_PLATEFORME}
     * n'est jamais suffisant seul pour cette méthode (T-05, sous-tâche 13).
     */
    public Page<Utilisateur> listerParEtablissementCourant(Pageable pageable) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        return utilisateurRepository.findParEtablissementCourantActif(etablissementId, pageable);
    }

    /**
     * Liste paginée des affectations (avec compte et rôles déjà chargés) de
     * l'établissement courant, pour {@code GET /api/v1/utilisateurs/mon-etablissement}.
     *
     * <p>N+1 corrigé (41 requêtes pour une page de 20) en deux requêtes,
     * jamais par {@code FetchType.EAGER} sur l'entité (CLAUDE.md, règle 10) :
     * une première requête pagine uniquement les identifiants
     * ({@link AffectationEtablissementRepository#findIdsParEtablissementCourantActif}),
     * une seconde charge le graphe complet (utilisateur + rôles) pour ce lot
     * borné ({@link AffectationEtablissementRepository#findByIdIn}). Paginer
     * directement une requête à {@code @EntityGraph} sur une collection
     * (les rôles) aurait fait basculer Hibernate en pagination mémoire
     * ({@code HHH000104}) — silencieusement incorrect au-delà de la première
     * page.</p>
     *
     * <p><strong>L'ordre est fixe (date de création croissante) et le tri
     * n'est volontairement pas exposé au paramètre {@code ?sort=}</strong> :
     * l'étape 1 ({@code findIdsParEtablissementCourantActif}) porte un
     * {@code order by} figé dans son JPQL, ignoré par tout {@link Pageable#getSort()}
     * qu'on lui passerait. Exposer un tri paramétrable casserait
     * silencieusement le découpage anti-N+1 ci-dessus (l'ordre des ids de
     * l'étape 1 ne correspondrait plus au tri demandé) sans qu'aucun test ne
     * l'attrape nécessairement — n'ajouter un tri ici qu'en propageant
     * {@code pageable.getSort()} dans la requête d'ids elle-même.</p>
     */
    public Page<AffectationEtablissement> listerAffectationsEtablissementCourant(Pageable pageable) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        Page<UUID> idsPage = affectationEtablissementRepository.findIdsParEtablissementCourantActif(etablissementId, pageable);

        List<UUID> ids = idsPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idsPage.getTotalElements());
        }

        List<AffectationEtablissement> affectationsNonOrdonnees = affectationEtablissementRepository.findByIdIn(ids);
        List<AffectationEtablissement> affectationsOrdonnees = ids.stream()
                .map(id -> affectationsNonOrdonnees.stream()
                        .filter(affectation -> affectation.getId().equals(id))
                        .findFirst()
                        .orElseThrow())
                .toList();

        return new PageImpl<>(affectationsOrdonnees, pageable, idsPage.getTotalElements());
    }

    /**
     * Crée un compte dans l'établissement courant.
     *
     * <p><strong>Faille corrigée (revue post-implémentation, 1re et 2e
     * passes) :</strong> cette méthode acceptait auparavant de rattacher une
     * affectation à un compte déjà existant ailleurs sur la plateforme
     * (modèle « un compte, N affectations », ADR-0002), avec un garde-fou
     * borné à tort à l'établissement courant. Un ADMIN de l'établissement A
     * pouvait ainsi saisir l'email d'un compte de l'établissement B (voire du
     * SUPER_ADMIN) : d'abord pour s'octroyer une affectation locale sans le
     * consentement du titulaire, puis (chemin en deux appels) pour en
     * récupérer le mot de passe via {@link #regenererMotDePasseTemporaire}.</p>
     *
     * <p><strong>Décision retenue (revue post-implémentation, 3e passe,
     * tranchée par le donneur d'ordre) :</strong> le rattachement est
     * supprimé, pas seulement verrouillé. Tout email déjà porté par un compte
     * <strong>actif</strong>, où qu'il soit sur la plateforme —
     * {@code superAdmin} compris, sans aucune distinction — est refusé en
     * 409 avec un message unique (voir {@link #validerEmailDisponible}). Même
     * raisonnement que pour {@code creerCompteAdministrateurInitial} avant
     * cette unification : un compte actif sans aucune affectation active est
     * un état qui ne doit jamais exister, donc cette réponse serait une
     * migration de réparation silencieuse, pas un endpoint de création qui
     * l'absorbe. Le modèle « un compte, N affectations » d'ADR-0002 reste
     * vrai <em>en base</em> (un compte affecté à deux établissements par une
     * autre voie, ex. donnée historique, continue de fonctionner
     * normalement) ; c'est son <strong>ouverture par cette API</strong> qui
     * est reportée à une US dédiée, avec un flux de consentement explicite du
     * titulaire — jamais une écriture unilatérale déclenchée par un tiers.</p>
     */
    @Transactional
    public CompteCree creerCompteAvecRoles(String email, String nomComplet, Set<RoleCode> roles, UUID siteId) {
        validerRolesAttribuables(roles);
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        String emailNormalise = email.toLowerCase(Locale.ROOT);
        validerEmailDisponible(emailNormalise);

        String motDePasseTemporaire = generateurMotDePasseTemporaire.generer();
        String motDePasseHache = passwordEncoder.encode(motDePasseTemporaire);

        Utilisateur utilisateur = new Utilisateur(emailNormalise, motDePasseHache, nomComplet, false);
        utilisateur.exigerChangementMotDePasse();
        utilisateur = utilisateurRepository.save(utilisateur);

        AffectationEtablissement affectation = new AffectationEtablissement(utilisateur, etablissementId, roles, siteId);
        affectation = affectationEtablissementRepository.save(affectation);

        // Le mot de passe temporaire n'est jamais stocké en clair : son hash
        // BCrypt alimente Utilisateur#motDePasseHache (login), son hash
        // SHA-256 le jeton d'activation (traçabilité/consommation, voir la
        // Javadoc de JetonActivationCompte).
        JetonActivationCompte jeton = new JetonActivationCompte(
                utilisateur, jetonHacheur.hacher(motDePasseTemporaire), Instant.now().plus(dureeValiditeMotDePasseTemporaire));
        jetonActivationCompteRepository.save(jeton);

        return new CompteCree(utilisateur, affectation, motDePasseTemporaire);
    }

    /**
     * Premier compte {@code ADMIN} d'un établissement fraîchement créé
     * (US-04 §3, consommée par {@code CreateurCompteAdministrateurImpl}) :
     * même comportement que {@link #creerCompteAvecRoles} depuis
     * l'unification des deux chemins (revue post-implémentation, 3e passe) —
     * l'admin initial d'un établissement neuf est nécessairement un compte
     * réellement créé, jamais rattaché.
     */
    @Transactional
    public CompteCree creerCompteAdministrateurInitial(String email, String nomComplet) {
        return creerCompteAvecRoles(email, nomComplet, EnumSet.of(RoleCode.ADMIN), null);
    }

    /**
     * Refuse la création si l'email est déjà porté par un compte actif,
     * n'importe où sur la plateforme (revue post-implémentation, 3e passe).
     *
     * <p><strong>Un seul message pour tous les cas</strong>, {@code superAdmin}
     * compris : distinguer « email libre » d'« email déjà pris » reste
     * nécessaire et acceptable (n'importe quel formulaire d'inscription le
     * concède), mais distinguer <em>quel type</em> de compte porte déjà
     * l'email ne l'est pas. Un message spécifique au cas {@code superAdmin}
     * ferait de cet endpoint un oracle permettant à un ADMIN client de tester
     * si un email correspond à un compte de plateforme Nova Digital — alors
     * qu'{@code /auth/login} refuse déjà, par construction, de révéler
     * l'existence d'un compte, et qu'ADR-0002 §5 interdit à un ADMIN toute
     * visibilité sur le périmètre plateforme.</p>
     */
    private void validerEmailDisponible(String emailNormalise) {
        if (utilisateurRepository.existsByEmailAndActifTrue(emailNormalise)) {
            throw new ConflitException("Cet email est déjà utilisé sur la plateforme.");
        }
    }

    /**
     * Remplace les rôles d'une affectation de l'établissement courant
     * ({@code ROLE_ATTRIBUER}). Refuse {@code SUPER_ADMIN} (rôle de
     * plateforme, jamais attribuable depuis un établissement) et refuse
     * qu'un appelant modifie ses propres rôles (élévation de privilège
     * triviale sinon).
     */
    @Transactional
    public void remplacerRoles(UUID utilisateurId, Set<RoleCode> nouveauxRoles, UUID appelantId) {
        if (utilisateurId.equals(appelantId)) {
            throw new RegleMetierViolee("Vous ne pouvez pas modifier vos propres rôles.");
        }
        validerRolesAttribuables(nouveauxRoles);

        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        AffectationEtablissement affectation = affectationEtablissementRepository
                .findByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        affectation.remplacerRoles(nouveauxRoles);
        affectationEtablissementRepository.save(affectation);
    }

    /**
     * Désactivation logique de l'affectation d'un compte dans l'établissement
     * courant ({@code UTILISATEUR_GERER}). Le compte {@code Utilisateur}
     * n'est désactivé (et ses refresh tokens révoqués) que s'il ne lui reste
     * plus aucune affectation active ailleurs. Refuse la désactivation de son
     * propre compte, et refuse de laisser l'établissement sans aucun ADMIN
     * actif (état impossible, jamais un état à réparer — spec US-04 §3).
     */
    @Transactional
    public void desactiverDansEtablissementCourant(UUID utilisateurId, UUID appelantId) {
        if (utilisateurId.equals(appelantId)) {
            throw new RegleMetierViolee("Vous ne pouvez pas désactiver votre propre compte.");
        }

        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        AffectationEtablissement affectation = affectationEtablissementRepository
                .findByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        if (affectation.getRoles().contains(RoleCode.ADMIN)) {
            long autresAdminsActifs = affectationEtablissementRepository
                    .compterAutresAdminsActifs(etablissementId, affectation.getId());
            if (autresAdminsActifs == 0) {
                throw new RegleMetierViolee("Impossible de désactiver le dernier administrateur actif de l'établissement.");
            }
        }

        affectation.desactiver();
        affectationEtablissementRepository.save(affectation);

        Utilisateur utilisateur = affectation.getUtilisateur();
        boolean autresAffectationsActives = affectationEtablissementRepository
                .existsByUtilisateurIdAndActifTrueAndIdNot(utilisateur.getId(), affectation.getId());
        if (!autresAffectationsActives) {
            utilisateur.desactiver();
            utilisateurRepository.save(utilisateur);
            revoquerJetonsActifs(utilisateur.getId());
        }
    }

    /**
     * Réactive l'affectation d'un compte dans l'établissement courant
     * ({@code UTILISATEUR_GERER}). Réactive aussi le compte {@code Utilisateur}
     * lui-même s'il avait été désactivé faute d'affectation active restante.
     *
     * <p>{@code uk_utilisateurs_email_actif} (V3) est un index <b>partiel</b>
     * ({@code WHERE actif = true}) : un email libéré par désactivation est
     * délibérément réutilisable pour un tout nouveau compte (CLAUDE.md,
     * règle 4). Réactiver ensuite l'ancien titulaire redeviendrait alors un
     * second compte actif portant le même email, ce que l'index refuserait en
     * base — mais seulement au moment du flush, en dehors de tout contrôle
     * applicatif clair. Le conflit est donc vérifié ici, explicitement, pour
     * lever un {@link ConflitException} lisible plutôt que de laisser
     * remonter une {@code ConstraintViolationException} brute (défaut détecté
     * par {@code GestionUtilisateursIntegrationTest#reactiver_restaureLAffectationEtLibereLEmailEstReutilisable}).</p>
     */
    @Transactional
    public void reactiverDansEtablissementCourant(UUID utilisateurId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        AffectationEtablissement affectation = affectationEtablissementRepository
                .findByUtilisateurIdAndEtablissementId(utilisateurId, etablissementId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        Utilisateur utilisateur = affectation.getUtilisateur();
        if (!utilisateur.isActif() && utilisateurRepository.existsByEmailAndActifTrue(utilisateur.getEmail())) {
            throw new ConflitException(
                    "Un autre compte actif porte désormais cet email : réactivation impossible.");
        }

        affectation.reactiver();
        affectationEtablissementRepository.save(affectation);

        if (!utilisateur.isActif()) {
            utilisateur.reactiver();
            utilisateurRepository.save(utilisateur);
        }
    }

    /**
     * Régénère un mot de passe temporaire pour un compte de l'établissement
     * courant ({@code UTILISATEUR_GERER}) : repose {@code motDePasseAChanger},
     * invalide les jetons d'activation précédents et révoque les refresh
     * tokens actifs (le titulaire doit se reconnecter avec le nouveau mot de
     * passe).
     *
     * <p><strong>Borne intentionnellement à un compte qui n'existe que dans
     * l'établissement courant</strong> (revue post-implémentation, point A) :
     * un ADMIN ne réinitialise le secret que d'un compte affecté uniquement
     * chez lui, jamais d'un compte qui porte aussi une affectation active
     * ailleurs — même message que « pas affecté localement » (404), pour ne
     * jamais révéler où est l'autre affectation. Cette borne est désormais la
     * <strong>seule</strong> ligne de défense contre la réinitialisation d'un
     * compte d'autrui : {@link #creerCompteAvecRoles} ne permet plus de créer
     * la situation par rattachement (revue post-implémentation, 3e passe),
     * mais un compte affecté à deux établissements par une autre voie (donnée
     * historique, insertion directe) reste possible en base — voir ADR-0002.</p>
     *
     * <p><strong>Conséquence à tracer, non résolue ici</strong> (revue
     * post-implémentation, 3e passe) : un compte légitimement partagé entre
     * deux établissements — l'enseignant de deux écoles, cas réel au Togo —
     * ne peut donc plus voir son mot de passe régénéré par <em>personne</em>,
     * chaque établissement voyant l'affectation active de l'autre comme un
     * motif de refus. Sans canal email avant le Sprint 10
     * (docs/adr/0006-notifications.md), ce compte n'a alors aucun chemin de
     * récupération s'il oublie son mot de passe. Cette US ne le résout pas :
     * c'est l'US de rattachement avec consentement explicite du titulaire
     * (voir la Javadoc de {@link #creerCompteAvecRoles}) qui devra fournir un
     * chemin de régénération légitime pour un compte multi-établissement.</p>
     */
    @Transactional
    public String regenererMotDePasseTemporaire(UUID utilisateurId) {
        UUID etablissementId = ContexteEtablissement.exigerEtablissementId();
        boolean affecte = affectationEtablissementRepository
                .existsByUtilisateurIdAndEtablissementIdAndActifTrue(utilisateurId, etablissementId);
        boolean affecteAilleursActif = affectationEtablissementRepository
                .existsByUtilisateurIdAndActifTrueAndEtablissementIdNot(utilisateurId, etablissementId);
        if (!affecte || affecteAilleursActif) {
            throw new RessourceIntrouvableException("Utilisateur introuvable.");
        }

        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        invaliderJetonsActivationActifs(utilisateurId);

        String motDePasseTemporaire = generateurMotDePasseTemporaire.generer();
        utilisateur.changerMotDePasseHache(passwordEncoder.encode(motDePasseTemporaire));
        utilisateur.exigerChangementMotDePasse();
        utilisateurRepository.save(utilisateur);

        JetonActivationCompte jeton = new JetonActivationCompte(
                utilisateur, jetonHacheur.hacher(motDePasseTemporaire), Instant.now().plus(dureeValiditeMotDePasseTemporaire));
        jetonActivationCompteRepository.save(jeton);

        revoquerJetonsActifs(utilisateurId);

        return motDePasseTemporaire;
    }

    /**
     * Changement de mot de passe par l'utilisateur lui-même : exige l'ancien
     * mot de passe, lève {@code motDePasseAChanger}, marque le(s) jeton(s)
     * d'activation actif(s) comme consommés et révoque les refresh tokens
     * actifs (le titulaire doit rouvrir une session avec le nouveau mot de
     * passe).
     */
    @Transactional
    public void changerMotDePasseSoiMeme(UUID utilisateurId, String ancienMotDePasse, String nouveauMotDePasse) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        if (!passwordEncoder.matches(ancienMotDePasse, utilisateur.getMotDePasseHache())) {
            throw new IdentifiantsInvalidesException("Ancien mot de passe incorrect.");
        }

        // Ne jamais évaluer avant la vérification ci-dessus (voir la Javadoc
        // de MotDePasseTemporaireExpireException) : sinon un appelant qui ne
        // connaît pas l'ancien mot de passe apprendrait que ce jeton est
        // expiré. Sans objet pour un compte normal (motDePasseAChanger = false).
        if (utilisateur.isMotDePasseAChanger()
                && jetonActivationCompteRepository.existsByUtilisateurIdAndActifTrueAndDateExpirationBefore(
                        utilisateurId, Instant.now())) {
            throw new MotDePasseTemporaireExpireException("Le mot de passe temporaire a expiré : demandez-en un nouveau.");
        }

        utilisateur.changerMotDePasseHache(passwordEncoder.encode(nouveauMotDePasse));
        utilisateur.confirmerChangementMotDePasse();
        utilisateurRepository.save(utilisateur);

        jetonActivationCompteRepository.findByUtilisateurIdAndActifTrue(utilisateurId).forEach(jeton -> {
            jeton.consommer();
            jeton.desactiver();
            jetonActivationCompteRepository.save(jeton);
        });

        revoquerJetonsActifs(utilisateurId);
    }

    @Transactional
    public void desactiverCompte(UUID utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable."));

        utilisateur.desactiver();
        utilisateurRepository.save(utilisateur);

        revoquerJetonsActifs(utilisateurId);
    }

    private void revoquerJetonsActifs(UUID utilisateurId) {
        List<JetonRafraichissement> jetonsActifs =
                jetonRafraichissementRepository.findByUtilisateurIdAndActifTrue(utilisateurId);
        jetonsActifs.forEach(JetonRafraichissement::desactiver);
        jetonsActifs.forEach(jetonRafraichissementRepository::save);
    }

    private void invaliderJetonsActivationActifs(UUID utilisateurId) {
        jetonActivationCompteRepository.findByUtilisateurIdAndActifTrue(utilisateurId).forEach(jeton -> {
            jeton.desactiver();
            jetonActivationCompteRepository.save(jeton);
        });
    }

    /**
     * {@code SUPER_ADMIN} est un rôle de plateforme (docs/adr/0002-multi-etablissement.md
     * §5), jamais attribuable depuis un établissement — que ce soit à la
     * création d'un compte ou par {@code PUT .../roles}.
     */
    private void validerRolesAttribuables(Set<RoleCode> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new RegleMetierViolee("Au moins un rôle doit être attribué.");
        }
        if (roles.contains(RoleCode.SUPER_ADMIN)) {
            throw new RegleMetierViolee("SUPER_ADMIN est un rôle de plateforme, non attribuable depuis un établissement.");
        }
    }

    /**
     * Compte et affectation résultant de {@link #creerCompteAvecRoles} ou
     * {@link #creerCompteAdministrateurInitial} : ces deux méthodes ne créent
     * plus qu'un compte réellement nouveau (revue post-implémentation, 3e
     * passe — le rattachement à un compte existant a été supprimé), donc
     * {@code motDePasseTemporaire} est toujours présent, à ne renvoyer qu'une
     * seule fois.
     */
    public record CompteCree(Utilisateur utilisateur, AffectationEtablissement affectation, String motDePasseTemporaire) {
    }
}
