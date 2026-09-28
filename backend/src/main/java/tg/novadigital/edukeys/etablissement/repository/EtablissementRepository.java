package tg.novadigital.edukeys.etablissement.repository;

import java.util.Optional;
import java.util.UUID;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;

public interface EtablissementRepository extends BaseRepository<Etablissement> {

    /** US-06 : résolution publique d'un établissement par son code, hors tout contexte multi-établissement. */
    Optional<Etablissement> findByCodeIgnoreCaseAndActifTrue(String code);

    boolean existsByCodeIgnoreCaseAndActifTrue(String code);

    boolean existsByEmailIgnoreCaseAndActifTrue(String email);

    boolean existsByEmailIgnoreCaseAndActifTrueAndIdNot(String email, UUID id);

    boolean existsByCodeIgnoreCaseAndActifTrueAndIdNot(String code, UUID id);
}
