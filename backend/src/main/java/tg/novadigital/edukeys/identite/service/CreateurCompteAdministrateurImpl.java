package tg.novadigital.edukeys.identite.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du port {@link CreateurCompteAdministrateur}, résolue par
 * Spring sur le type de l'interface dans le module {@code etablissement}
 * (CLAUDE.md, règle 1). Délègue à
 * {@link UtilisateurService#creerCompteAdministrateurInitial} —
 * <strong>pas</strong> {@code creerCompteAvecRoles} (revue post-implémentation) :
 * ce dernier accepte de rattacher une affectation à un compte déjà existant
 * ailleurs sur la plateforme, ce qui est correct pour {@code POST /api/v1/utilisateurs}
 * (modèle « un compte, N affectations ») mais pas ici — un SUPER_ADMIN qui
 * crée un établissement en saisissant l'email d'un ADMIN client existant ne
 * doit jamais en prendre le contrôle. {@code creerCompteAdministrateurInitial}
 * refuse la création (409) si l'email est déjà porté par un compte actif.
 */
@Service
public class CreateurCompteAdministrateurImpl implements CreateurCompteAdministrateur {

    private final UtilisateurService utilisateurService;

    public CreateurCompteAdministrateurImpl(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    @Override
    @Transactional
    public CompteAdministrateurCree creerAdministrateur(UUID etablissementId, String email, String nomComplet) {
        // etablissementId n'est pas utilisé directement ici : la méthode
        // déléguée lit l'établissement courant depuis le contexte
        // multi-établissement déjà ouvert par l'appelant (EtablissementService#creer,
        // CLAUDE.md règle 12 pour le flush avant fermeture de cette portée).
        UtilisateurService.CompteCree compteCree = utilisateurService.creerCompteAdministrateurInitial(email, nomComplet);
        return new CompteAdministrateurCree(
                compteCree.utilisateur().getId(), compteCree.utilisateur().getEmail(), compteCree.motDePasseTemporaire());
    }
}
