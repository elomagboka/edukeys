package tg.novadigital.edukeys.identite.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.identite.domain.AffectationEtablissement;

public interface AffectationEtablissementRepository extends BaseRepository<AffectationEtablissement> {

    @EntityGraph(attributePaths = "roles")
    List<AffectationEtablissement> findByUtilisateurIdAndActifTrueOrderByDateCreationAsc(UUID utilisateurId);

    boolean existsByUtilisateurIdAndEtablissementIdAndActifTrue(UUID utilisateurId, UUID etablissementId);
}
