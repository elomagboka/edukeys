package tg.novadigital.edukeys.common.exception;

/**
 * Catalogue fermé des codes d'erreur machine portés par les réponses
 * {@link org.springframework.http.ProblemDetail} (propriété {@code code}).
 *
 * <p>Un code décrit la <strong>cause</strong>, jamais le statut HTTP
 * (ex. {@code …_INTROUVABLE}, pas {@code …_404}), et n'est jamais réutilisé
 * pour deux causes distinctes même à statut et message identiques : le
 * frontend s'en sert pour distinguer des situations qui appellent des
 * actions différentes (voir CLAUDE.md, convention DELTA 3 de l'US-01).</p>
 *
 * <p>Convention : {@code DOMAINE_OBJET_SITUATION}, {@code SCREAMING_SNAKE_CASE},
 * français, sans accent ni caractère non-ASCII (le code transite en JSON et
 * sert de clé de traduction côté front).</p>
 */
public enum CodeErreur {

    // Établissement
    ETABLISSEMENT_INTROUVABLE,
    ETABLISSEMENT_CODE_DUPLIQUE,
    ETABLISSEMENT_EMAIL_DUPLIQUE,
    ETABLISSEMENT_CODE_REPRIS_DEPUIS_DESACTIVATION,
    ETABLISSEMENT_EMAIL_REPRIS_DEPUIS_DESACTIVATION,

    // Site
    SITE_INTROUVABLE,
    SITE_CODE_DUPLIQUE,
    SITE_PRINCIPAL_NON_DESACTIVABLE,

    // Logo
    LOGO_INTROUVABLE,
    LOGO_VIDE,
    LOGO_ILLISIBLE,

    // Historique
    HISTORIQUE_INTROUVABLE,

    // Identité / utilisateurs
    UTILISATEUR_INTROUVABLE,
    UTILISATEUR_EMAIL_DUPLIQUE,
    UTILISATEUR_EMAIL_REPRIS_DEPUIS_DESACTIVATION,
    ROLES_AUTO_MODIFICATION_REFUSEE,
    COMPTE_AUTO_DESACTIVATION_REFUSEE,
    DERNIER_ADMINISTRATEUR_NON_DESACTIVABLE,
    ROLE_OBLIGATOIRE,
    ROLE_SUPER_ADMIN_NON_ATTRIBUABLE,

    // Authentification
    IDENTIFIANTS_INVALIDES,
    MOT_DE_PASSE_TEMPORAIRE_EXPIRE,
    COMPTE_DESACTIVE,
    AFFECTATION_ABSENTE,

    // Fichiers
    FORMAT_FICHIER_NON_SUPPORTE,
    FICHIER_TROP_VOLUMINEUX,

    // Années scolaires (US-01)
    ANNEE_SCOLAIRE_INTROUVABLE,
    ANNEE_SCOLAIRE_ACTIVE_ABSENTE,
    ANNEE_SCOLAIRE_LIBELLE_DUPLIQUE,
    ANNEE_SCOLAIRE_PERIODE_CHEVAUCHANTE,
    ANNEE_SCOLAIRE_DATES_INCOHERENTES,
    ANNEE_SCOLAIRE_DUREE_INVALIDE,
    ANNEE_SCOLAIRE_LIBELLE_VIDE,
    ANNEE_SCOLAIRE_TRANSITION_INVALIDE,
    ANNEE_SCOLAIRE_CLOTUREE_IMMUABLE,
    ANNEE_SCOLAIRE_DESACTIVATION_REFUSEE,
    /**
     * Traduction d'une {@code DataIntegrityViolationException} sur
     * {@code uk_annees_scolaires_active} (course entre deux activations
     * concurrentes, point d'attention 4 de la spec US-01) : la vérification
     * applicative a laissé passer les deux, la contrainte de base tranche.
     */
    ANNEE_SCOLAIRE_ACTIVATION_CONCURRENTE,

    // Inter-établissement
    ECRITURE_INTER_ETABLISSEMENT_REFUSEE,

    // Génériques, posés par le handler lui-même (pas par une EdukeysException)
    ACCES_REFUSE,
    REQUETE_INVALIDE,
    CORPS_ILLISIBLE,
    TROP_DE_REQUETES,
    ERREUR_INATTENDUE
}
