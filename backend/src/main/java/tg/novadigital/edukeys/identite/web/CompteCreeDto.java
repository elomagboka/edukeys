package tg.novadigital.edukeys.identite.web;

/**
 * Réponse de {@code POST /api/v1/utilisateurs} : le compte nouvellement créé
 * et le mot de passe temporaire de son titulaire, retourné une seule fois —
 * jamais relogué, jamais renvoyé par une lecture ultérieure.
 *
 * <p>Toujours une création (revue post-implémentation, 3e passe) : le
 * rattachement d'une affectation à un compte déjà existant a été supprimé
 * (voir la Javadoc de {@code UtilisateurService#creerCompteAvecRoles}), donc
 * {@code motDePasseTemporaire} est toujours présent — plus de distinction à
 * faire côté consommateur de l'API.</p>
 */
public record CompteCreeDto(UtilisateurCompteDto compte, String motDePasseTemporaire) {
}
