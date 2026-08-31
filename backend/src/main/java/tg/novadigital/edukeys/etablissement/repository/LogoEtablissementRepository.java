package tg.novadigital.edukeys.etablissement.repository;

import java.util.Optional;
import java.util.UUID;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.etablissement.domain.LogoEtablissement;

public interface LogoEtablissementRepository extends BaseRepository<LogoEtablissement> {

    Optional<LogoEtablissement> findByEtablissementIdAndActifTrue(UUID etablissementId);

    /** Réactivation en cascade (EtablissementService#reactiver) : le logo désactivé avec son établissement. */
    Optional<LogoEtablissement> findByEtablissementIdAndActifFalse(UUID etablissementId);
}
