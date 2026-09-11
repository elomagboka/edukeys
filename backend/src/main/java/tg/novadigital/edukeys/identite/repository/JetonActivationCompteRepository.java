package tg.novadigital.edukeys.identite.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.identite.domain.JetonActivationCompte;

public interface JetonActivationCompteRepository extends BaseRepository<JetonActivationCompte> {

    /** Utilisé pour invalider tout jeton précédent lorsqu'un nouveau mot de passe temporaire est émis (US-04). */
    List<JetonActivationCompte> findByUtilisateurIdAndActifTrue(UUID utilisateurId);

    /**
     * Vrai si le jeton d'activation actif du compte est expiré (US-04) :
     * branché sur {@code /auth/login} et {@code changerMotDePasseSoiMeme},
     * jamais avant qu'un mot de passe se soit révélé correct par ailleurs
     * (voir {@link tg.novadigital.edukeys.common.exception.MotDePasseTemporaireExpireException}).
     * Une comparaison de dates en base, plutôt que charger l'entité pour
     * appeler {@code estExpire()} : ce chemin est traversé à chaque
     * connexion d'un compte {@code motDePasseAChanger = true}.
     */
    boolean existsByUtilisateurIdAndActifTrueAndDateExpirationBefore(UUID utilisateurId, Instant instant);
}
