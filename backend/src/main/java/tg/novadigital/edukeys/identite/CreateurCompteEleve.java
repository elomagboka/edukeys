package tg.novadigital.edukeys.identite;

import java.util.UUID;

/**
 * Port exposé par le module {@code identite} au module {@code eleve} (US-08) pour créer le compte
 * d'un élève fraîchement inscrit (CLAUDE.md, règle 1 : {@code eleve} n'importe jamais
 * {@code UtilisateurService}). Doit être appelé dans la transaction de l'inscription
 * ({@code MANDATORY}) et dans un contexte d'établissement ouvert.
 *
 * <p>Crée toujours un compte <strong>neuf</strong> : identifiant de connexion = matricule normalisé,
 * sans email, rôle {@code ELEVE} seul, sans site, changement de mot de passe exigé, mot de passe
 * stocké = hash d'un secret aléatoire que personne ne reçoit, aucun jeton d'activation. Le mot de passe
 * temporaire réel est émis ensuite par {@link EmetteurMotDePasseTemporaire}.</p>
 */
public interface CreateurCompteEleve {

    /**
     * @return l'identifiant du compte créé
     * @throws tg.novadigital.edukeys.common.exception.ConflitException {@code UTILISATEUR_IDENTIFIANT_DUPLIQUE} (409)
     *         si l'identifiant est déjà porté par un compte actif
     */
    UUID creerCompteEleve(String matricule, String nomComplet);
}
