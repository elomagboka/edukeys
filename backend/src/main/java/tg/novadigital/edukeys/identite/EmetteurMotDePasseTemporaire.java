package tg.novadigital.edukeys.identite;

import java.time.Instant;
import java.util.UUID;

/**
 * Port exposé par le module {@code identite} aux modules qui créent leurs
 * propres comptes (US-08 : comptes élèves) pour émettre un mot de passe
 * temporaire à expiration explicite (CLAUDE.md, règle 1 : les autres modules
 * n'importent jamais {@code UtilisateurService}).
 *
 * <p><strong>Bornée comme {@code regenererMotDePasseTemporaire}</strong> : doit
 * être appelée dans un contexte d'établissement ouvert, et ne réussit que pour un
 * compte actif, non {@code superAdmin}, affecté à l'établissement courant
 * <em>et nulle part ailleurs</em>, dont l'affectation courante ne porte
 * <strong>QUE</strong> les rôles {@code ELEVE} ou {@code PARENT} (un compte qui cumule
 * un rôle du personnel, ex. ADMIN + PARENT, est refusé). Tout autre compte est refusé comme
 * « introuvable » (même message qu'un identifiant inexistant). Une
 * implémentation de ce port qui s'affranchirait de ces bornes ouvrirait une
 * porte de prise de contrôle de compte : voir {@code GardeBornesSecretsIntegrationTest}.</p>
 */
public interface EmetteurMotDePasseTemporaire {

    /**
     * Pose un mot de passe temporaire expirant à {@code dateExpiration}, exige
     * son changement au premier accès, invalide les jetons d'activation
     * précédents et révoque les sessions du compte.
     *
     * @return le mot de passe en clair, à remettre une seule fois
     * @throws IllegalArgumentException si la date est nulle ou passée (erreur de programmation de l'appelant)
     * @throws tg.novadigital.edukeys.common.exception.RegleMetierViolee {@code MOT_DE_PASSE_TEMPORAIRE_EXPIRATION_HORS_BORNES}
     *         (422) si la date dépasse {@code edukeys.securite.mot-de-passe-temporaire.expiration-max} : règle métier
     *         (inscription trop anticipée), jamais plafonnée en silence
     * @throws tg.novadigital.edukeys.common.exception.RessourceIntrouvableException
     *         si le compte n'est pas dans le périmètre décrit ci-dessus
     */
    String emettreAvecExpiration(UUID utilisateurId, Instant dateExpiration);
}
