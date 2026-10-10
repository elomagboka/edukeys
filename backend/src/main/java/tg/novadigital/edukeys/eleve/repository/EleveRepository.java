package tg.novadigital.edukeys.eleve.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tg.novadigital.edukeys.common.repository.BaseRepository;
import tg.novadigital.edukeys.eleve.domain.Eleve;

public interface EleveRepository extends BaseRepository<Eleve> {

    /**
     * Homonymes actifs (US-08, Q4) : même nom, mêmes prénoms (normalisés) et même date de naissance, avec
     * la classe de leur inscription active la plus récente s'il y en a une.
     * La requête renvoie une ligne par inscription active : le service dédoublonne par élève (matricule) en
     * gardant la première, donc la plus récente grâce au tri. UNE requête (index
     * {@code idx_eleves_homonymie}), jamais une requête par homonyme (CLAUDE.md, règle 10).
     */
    @Query("""
            select new tg.novadigital.edukeys.eleve.repository.EleveRepository$Homonyme(e.matricule, i.classeId, i.dateInscription)
            from Eleve e
            left join Inscription i on i.eleve = e and i.actif = true
            where e.etablissementId = :etablissementId
              and e.actif = true
              and e.nomNormalise = :nomNormalise
              and e.prenomsNormalises = :prenomsNormalises
              and e.dateNaissance = :dateNaissance
            order by e.matricule, i.dateInscription desc
            """)
    List<Homonyme> rechercherHomonymes(
            @Param("etablissementId") UUID etablissementId,
            @Param("nomNormalise") String nomNormalise,
            @Param("prenomsNormalises") String prenomsNormalises,
            @Param("dateNaissance") LocalDate dateNaissance);

    /** @param classeId {@code null} si l'élève n'a aucune inscription active */
    record Homonyme(String matricule, UUID classeId, java.time.Instant dateInscription) {
    }
}
