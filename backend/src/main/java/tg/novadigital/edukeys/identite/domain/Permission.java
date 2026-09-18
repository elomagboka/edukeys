package tg.novadigital.edukeys.identite.domain;

/**
 * Catalogue fermé des permissions élémentaires d'Edukeys. Une permission
 * n'existe qu'attachée à un ou plusieurs {@link RoleCode} — voir
 * {@link RoleCode#getPermissions()}. Énumération plutôt qu'entité JPA
 * (correction T-04, lot 1 n°2) : le référentiel RBAC ne change jamais sans
 * déploiement de toute façon (il est câblé dans le code), donc le modéliser
 * en base n'achetait que le coût d'une requête en base à chaque requête HTTP
 * pour résoudre les permissions effectives d'un rôle — précisément ce que la
 * spec T-04 voulait éviter.
 */
public enum Permission {
    /** Création, désactivation et réactivation d'un établissement (US-00) : réservée à SUPER_ADMIN, jamais ADMIN — ADMIN gère SON établissement déjà créé, pas la plateforme. */
    ETABLISSEMENT_CREER("Créer un établissement"),
    ETABLISSEMENT_GERER("Gérer un établissement"),
    UTILISATEUR_GERER("Gérer les utilisateurs de son établissement"),
    /**
     * Distincte de {@link #UTILISATEUR_GERER} : porte sur les comptes de
     * TOUS les établissements (endpoint {@code GET /api/v1/utilisateurs}).
     * Réservée à {@code SUPER_ADMIN} — {@code ADMIN} porte {@code UTILISATEUR_GERER}
     * mais reste borné à son établissement (ADR-0002 §5). Les confondre a déjà
     * causé une fuite inter-établissement (relecture T-04, repasse n°2) :
     * ADMIN portait aussi UTILISATEUR_GERER et obtenait donc la liste globale.
     */
    UTILISATEUR_GERER_PLATEFORME("Gérer les utilisateurs de tous les établissements"),
    /**
     * Lecture des comptes de son établissement, distincte de
     * {@link #UTILISATEUR_GERER} (US-04) : {@code DIRECTION} doit pouvoir
     * consulter le personnel de son établissement sans pouvoir créer,
     * désactiver ou réaffecter un compte.
     */
    UTILISATEUR_CONSULTER("Consulter les utilisateurs de son établissement"),
    /**
     * Attribution des rôles d'une affectation, volontairement séparée de
     * {@link #UTILISATEUR_GERER} (US-04). Sans cette scission, gérer les
     * comptes équivaudrait à pouvoir s'octroyer n'importe quelle permission :
     * un {@code ADMIN} porteur d'une seule permission « gérer les
     * utilisateurs » pourrait se créer un compte, puis lui attribuer
     * {@code SUPER_ADMIN} — élévation de privilège triviale. En pratique
     * {@code ROLE_ATTRIBUER} reste, elle aussi, refusée sur {@code SUPER_ADMIN}
     * (rôle de plateforme, jamais attribuable depuis un établissement) : voir
     * la vérification dans {@code UtilisateurService}.
     */
    ROLE_ATTRIBUER("Attribuer les rôles d'un compte de son établissement"),
    /** Consultation des années scolaires de son établissement (US-01). */
    ANNEE_SCOLAIRE_CONSULTER("Consulter les années scolaires de son établissement"),
    /**
     * Gestion des années scolaires (création, modification, activation,
     * clôture, désactivation — US-01). Volontairement distincte de
     * {@link #ETABLISSEMENT_GERER} : réutiliser cette dernière élargirait
     * silencieusement sa portée (CLAUDE.md, règle 11 — piège déjà rencontré
     * sur {@code SiteController}/{@code LogoController}).
     */
    ANNEE_SCOLAIRE_GERER("Gérer les années scolaires de son établissement"),
    /** Consultation de la structure académique — cycles, niveaux, filières, classes (US-02). */
    STRUCTURE_ACADEMIQUE_CONSULTER("Consulter la structure académique de son établissement"),
    /**
     * Gestion de la structure académique (US-02). Une seule permission pour
     * les quatre objets (cycle, niveau, filière, classe) : ils forment un
     * même référentiel administré par les mêmes personnes en même temps —
     * démultiplier les permissions ici ne protège rien. Volontairement
     * distincte d'{@link #ETABLISSEMENT_GERER} (CLAUDE.md, règle 11).
     */
    STRUCTURE_ACADEMIQUE_GERER("Gérer la structure académique de son établissement"),
    /** Consultation des matières et de leurs affectations (niveau/filière) — US-03. */
    MATIERE_CONSULTER("Consulter les matières de son établissement"),
    /**
     * Gestion des matières (création, modification, affectations, désactivation
     * — US-03). Distincte de {@link #STRUCTURE_ACADEMIQUE_GERER} : les matières
     * forment un référentiel séparé de cycles/niveaux/filières/classes, avec
     * son propre cycle de vie et ses propres règles de cohérence (CLAUDE.md,
     * règle 11 — ne pas élargir silencieusement une permission existante).
     */
    MATIERE_GERER("Gérer les matières de son établissement"),
    NOTE_SAISIR("Saisir des notes"),
    DEVOIR_CREER("Créer un devoir"),
    ENFANT_CONSULTER("Consulter le dossier enfant"),
    BULLETIN_CONSULTER("Consulter un bulletin");

    private final String libelle;

    Permission(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
