package tg.novadigital.edukeys.admission.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * déjà EN_ATTENTE ou LISTE_ATTENTE pour la même identité, sur la même
     * année. Requête JPQL, pas native (CLAUDE.md, règle 2).
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
                                tg.novadigital.edukeys.admission.domain.StatutAdmission.LISTE_ATTENTE)
            """)
    List<DemandeAdmission> rechercherDoublonsActifs(
            @Param("etablissementId") UUID etablissementId,
            @Param("anneeScolaireId") UUID anneeScolaireId,
            @Param("nomNormalise") String nomNormalise,
            @Param("prenomsNormalises") String prenomsNormalises,
            @Param("dateNaissance") LocalDate dateNaissance);
}
