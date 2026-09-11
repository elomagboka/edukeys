package tg.novadigital.edukeys.common.exception;

/**
 * Le mot de passe temporaire présenté est correct, mais son jeton
 * d'activation ({@code JetonActivationCompte}) est expiré (US-04) : distincte
 * de {@link IdentifiantsInvalidesException} précisément pour que
 * l'administrateur sache qu'il doit régénérer un nouveau mot de passe
 * temporaire ({@code POST /api/v1/utilisateurs/{id}/mot-de-passe-temporaire}),
 * pas qu'il l'a mal recopié.
 *
 * <p>Ne doit jamais être levée avant qu'un mot de passe se soit révélé
 * correct par ailleurs : sinon la distinction d'avec
 * {@code IdentifiantsInvalidesException} fuiterait, à un attaquant qui ne
 * connaît pas le mot de passe, l'information que ce compte porte un mot de
 * passe temporaire expiré.</p>
 */
public class MotDePasseTemporaireExpireException extends EdukeysException {

    public MotDePasseTemporaireExpireException(String message) {
        super(message);
    }
}
