package tg.novadigital.edukeys.academique.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tg.novadigital.edukeys.academique.domain.PeriodeAcademique;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface PeriodeAcademiqueRepository extends BaseRepository<PeriodeAcademique> {

    List<PeriodeAcademique> findByEtablissementIdAndAnneeScolaireIdAndActifTrueOrderByOrdreAsc(
            UUID etablissementId, UUID anneeScolaireId);

    List<PeriodeAcademique> findByEtablissementIdAndAnneeScolaireIdOrderByOrdreAsc(
            UUID etablissementId, UUID anneeScolaireId);

    List<PeriodeAcademique> findByEtablissementIdAndActifTrueOrderByOrdreAsc(UUID etablissementId);

    List<PeriodeAcademique> findByEtablissementIdOrderByOrdreAsc(UUID etablissementId);

    boolean existsByEtablissementIdAndAnneeScolaireIdAndLibelleAndActifTrue(
            UUID etablissementId, UUID anneeScolaireId, String libelle);

    boolean existsByEtablissementIdAndAnneeScolaireIdAndOrdreAndActifTrue(
            UUID etablissementId, UUID anneeScolaireId, int ordre);

    /** R7 : période active de l'année donnée dont la plage inclut {@code date} (bornes comprises). */
    @Query("""
            select p from PeriodeAcademique p
            where p.etablissementId = :etablissementId
              and p.anneeScolaireId = :anneeScolaireId
              and p.actif = true
              and p.dateDebut <= :date
              and p.dateFin >= :date
            """)
    Optional<PeriodeAcademique> trouverEnCours(@Param("etablissementId") UUID etablissementId,
                                                @Param("anneeScolaireId") UUID anneeScolaireId,
                                                @Param("date") LocalDate date);
}
