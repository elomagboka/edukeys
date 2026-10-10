package tg.novadigital.edukeys.admission.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import tg.novadigital.edukeys.admission.domain.DemandeAdmission;
import tg.novadigital.edukeys.admission.domain.StatutAdmission;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface DemandeAdmissionRepository extends BaseRepository<DemandeAdmission> {

    Page<DemandeAdmission> findByEtablissementIdAndActifTrueOrderByDateSoumissionDesc(UUID etablissementId, Pageable pageable);

    Page<DemandeAdmission> findByEtablissementIdAndActifTrueAndStatutOrderByDateSoumissionDesc(
            UUID etablissementId, StatutAdmission statut, Pageable pageable);

    boolean existsByEtablissementIdAndReferenceAndActifTrue(UUID etablissementId, String reference);

    /**
     * Idempotence de la soumission (règle 3, spec US-06) : un dossier actif
     * déjà EN_ATTENTE, LISTE_ATTENTE ou ACCEPTEE (US-08, Q5 : un enfant accepté
     * reste « vivant », même inscrit) pour la même identité, sur la même année —
     * même définition que l'index {@code uk_demandes_admission_doublon} (V16). Requête JPQL, pas native (CLAUDE.md, règle 2).
     */
    @Query("""
            select d from DemandeAdmission d
            where d.etablissementId = :etablissementId
              and d.anneeScolaireId = :anneeScolaireId
              and d.actif = true
              and d.nomNormalise = :nomNormalise
              and d.prenomsNormalises = :prenomsNormalises
              and d.dateNaissance = :dateNaissance
              and d.statut in (tg.novadigital.edukeys.admission.domain.StatutAdmission.EN_ATTENTE,
                                tg.novadigital.edukeys.admission.domain.StatutAdmission.LISTE_ATTENTE,
                                tg.novadigital.edukeys.admission.domain.StatutAdmission.ACCEPTEE)
            """)
    List<DemandeAdmission> rechercherDoublonsActifs(
            @Param("etablissementId") UUID etablissementId,
            @Param("anneeScolaireId") UUID anneeScolaireId,
            @Param("nomNormalise") String nomNormalise,
            @Param("prenomsNormalises") String prenomsNormalises,
            @Param("dateNaissance") LocalDate dateNaissance);

    /**
     * Verrou pessimiste du dossier (US-08) : sérialise deux inscriptions simultanées du même
     * dossier (double clic). JPQL, jamais natif (CLAUDE.md, règle 2) ; le filtre multi-établissement
     * s'applique, et le prédicat explicite le rend lisible.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DemandeAdmission d where d.id = :id and d.etablissementId = :etablissementId and d.actif = true")
    Optional<DemandeAdmission> trouverPourVerrouiller(@Param("id") UUID id, @Param("etablissementId") UUID etablissementId);
}
