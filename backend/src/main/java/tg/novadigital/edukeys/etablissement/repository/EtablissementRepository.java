package tg.novadigital.edukeys.etablissement.repository;

import java.util.UUID;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;

public interface EtablissementRepository extends BaseRepository<Etablissement> {

    boolean existsByCodeIgnoreCaseAndActifTrue(String code);

    boolean existsByEmailIgnoreCaseAndActifTrue(String email);

    boolean existsByEmailIgnoreCaseAndActifTrueAndIdNot(String email, UUID id);

    boolean existsByCodeIgnoreCaseAndActifTrueAndIdNot(String code, UUID id);
}
