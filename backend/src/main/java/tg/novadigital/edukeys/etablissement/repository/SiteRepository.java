package tg.novadigital.edukeys.etablissement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.etablissement.domain.Site;

public interface SiteRepository extends BaseRepository<Site> {

    List<Site> findByEtablissementIdAndActifTrueOrderByNomAsc(UUID etablissementId);

    /** Réactivation en cascade (EtablissementService#reactiver) : les sites désactivés avec leur établissement. */
    List<Site> findByEtablissementIdAndActifFalseOrderByNomAsc(UUID etablissementId);

    Optional<Site> findByEtablissementIdAndCodeIgnoreCaseAndActifTrue(UUID etablissementId, String code);

    Optional<Site> findByEtablissementIdAndPrincipalTrueAndActifTrue(UUID etablissementId);
}
