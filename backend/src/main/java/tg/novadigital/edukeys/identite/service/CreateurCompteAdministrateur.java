package tg.novadigital.edukeys.identite.service;

import java.util.UUID;

/**
 * Interface exposée par le module {@code identite} pour créer le premier
 * compte administrateur d'un établissement, consommée par le module
 * {@code etablissement} lors de {@code EtablissementService#creer}
 * (US-04 §3, US-00/T-10).
 *
 * <p>Un établissement sans administrateur ne doit jamais exister, même
 * transitoirement : plutôt qu'un endpoint de réparation, {@code creer}
 * appelle ce port dans la même transaction que la création de
 * l'établissement — même raisonnement que le site principal auto-créé en
 * T-10.</p>
 *
 * <p>Application stricte de CLAUDE.md, règle 1 : {@code etablissement}
 * n'importe que cette interface (et le petit record qu'elle renvoie), jamais
 * {@code Utilisateur}, {@code AffectationEtablissement} ni
 * {@code UtilisateurRepository}. L'implémentation ({@code identite.service})
 * reste invisible du module appelant, résolue par injection Spring sur le
 * type de l'interface.</p>
 */
public interface CreateurCompteAdministrateur {

    /**
     * Crée (ou affecte, si un compte porte déjà cet email ailleurs — modèle
     * « un utilisateur, un compte, N affectations », ADR-0002) le compte
     * {@code ADMIN} de l'établissement donné. Doit être appelée alors qu'un
     * contexte multi-établissement pointant sur {@code etablissementId} est
     * déjà ouvert (CLAUDE.md, règle 12 : le service appelant doit forcer le
     * flush avant de fermer sa {@code PorteeEtablissement}).
     *
     * @return l'identifiant du compte et son mot de passe temporaire, à
     *         retourner une seule fois dans la réponse de création
     *         d'établissement.
     */
    CompteAdministrateurCree creerAdministrateur(UUID etablissementId, String email, String nomComplet);

    record CompteAdministrateurCree(UUID utilisateurId, String email, String motDePasseTemporaire) {
    }
}
