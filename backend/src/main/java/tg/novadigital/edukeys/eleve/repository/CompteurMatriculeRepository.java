package tg.novadigital.edukeys.eleve.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.eleve.domain.CompteurMatricule;

public interface CompteurMatriculeRepository extends BaseRepository<CompteurMatricule> {

    /**
     * Verrou pessimiste (JPQL, jamais natif — CLAUDE.md, règle 2) : deux inscriptions simultanées
     * ne doivent jamais obtenir le même numéro. Ne filtre PAS sur {@code actif} : un compteur n'est
     * jamais désactivé et son unicité est absolue.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CompteurMatricule c where c.etablissementId = :etablissementId and c.annee = :annee")
    Optional<CompteurMatricule> trouverPourVerrouiller(@Param("etablissementId") UUID etablissementId, @Param("annee") int annee);

    /** Lecture simple, sans verrou : sert à la création idempotente de la ligne à zéro. */
    boolean existsByEtablissementIdAndAnnee(UUID etablissementId, int annee);
}
