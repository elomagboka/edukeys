package tg.novadigital.edukeys.admission.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import tg.novadigital.edukeys.admission.domain.CompteurReferenceAdmission;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface CompteurReferenceAdmissionRepository extends BaseRepository<CompteurReferenceAdmission> {

    /**
     * Verrou pessimiste (règle du CLAUDE.md : pas de SQL natif sur une
     * entité métier) : deux soumissions simultanées pour la même année ne
     * doivent jamais obtenir la même séquence. Requête JPQL en lecture, le
     * verrou est posé par {@link Lock}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompteurReferenceAdmission c where c.etablissementId = :etablissementId and c.annee = :annee and c.actif = true")
    Optional<CompteurReferenceAdmission> trouverPourVerrouiller(@Param("etablissementId") UUID etablissementId, @Param("annee") int annee);
}
