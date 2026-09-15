package tg.novadigital.edukeys.academique.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.domain.StatutAnneeScolaire;
import tg.novadigital.edukeys.common.repository.BaseRepository;

public interface AnneeScolaireRepository extends BaseRepository<AnneeScolaire> {

    List<AnneeScolaire> findByEtablissementIdAndActifTrueOrderByDateDebutDesc(UUID etablissementId);

    List<AnneeScolaire> findByEtablissementIdAndActifTrueAndStatutOrderByDateDebutDesc(
            UUID etablissementId, StatutAnneeScolaire statut);

    List<AnneeScolaire> findByEtablissementIdOrderByDateDebutDesc(UUID etablissementId);

    List<AnneeScolaire> findByEtablissementIdAndStatutOrderByDateDebutDesc(UUID etablissementId, StatutAnneeScolaire statut);

    Optional<AnneeScolaire> findByEtablissementIdAndStatutAndActifTrue(UUID etablissementId, StatutAnneeScolaire statut);

    boolean existsByEtablissementIdAndLibelleAndActifTrue(UUID etablissementId, String libelle);

    /**
     * R5 (Vérification applicative, avant la contrainte d'exclusion
     * {@code ex_annees_scolaires_chevauchement} — DELTA 2). Requête JPQL, pas
     * native : elle passe par le filtre Hibernate multi-établissement comme
     * toute autre requête sur cette entité (CLAUDE.md, règle 2).
     * {@code excludeId} nullable : absent à la création, renseigné à la
     * modification pour ignorer l'année elle-même.
     */
    @Query("""
            select a from AnneeScolaire a
            where a.etablissementId = :etablissementId
              and a.actif = true
              and (:excludeId is null or a.id <> :excludeId)
              and a.dateDebut <= :dateFin
              and a.dateFin >= :dateDebut
            """)
    List<AnneeScolaire> rechercherChevauchements(
            @Param("etablissementId") UUID etablissementId,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin,
            @Param("excludeId") UUID excludeId);
}
