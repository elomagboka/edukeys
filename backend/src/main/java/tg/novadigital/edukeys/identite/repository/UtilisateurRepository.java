package tg.novadigital.edukeys.identite.repository;

import java.util.Optional;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.identite.domain.Utilisateur;

public interface UtilisateurRepository extends BaseRepository<Utilisateur> {

    Optional<Utilisateur> findByEmailAndActifTrue(String email);

    boolean existsByEmailAndActifTrue(String email);
}
