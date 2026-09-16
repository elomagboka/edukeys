package tg.novadigital.edukeys.academique.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import tg.novadigital.edukeys.academique.domain.Classe;
import tg.novadigital.edukeys.academique.web.ClasseDto;
import tg.novadigital.edukeys.academique.web.ClasseHistoriqueDto;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;

/**
 * {@code cycleId}/{@code cycleLibelle} proviennent de {@code niveau.cycle}
 * (chemin à deux sauts, chargé par {@code @EntityGraph} côté repository —
 * point d'attention N+1 de la spec, jamais naviguée ici sans précaution
 * amont). {@code filiere} et sa navigation restent nullable (D4).
 */
@Mapper(componentModel = "spring")
public interface ClasseMapper {

    @Mapping(source = "niveau.id", target = "niveauId")
    @Mapping(source = "niveau.libelle", target = "niveauLibelle")
    @Mapping(source = "niveau.cycle.id", target = "cycleId")
    @Mapping(source = "niveau.cycle.libelle", target = "cycleLibelle")
    @Mapping(source = "filiere.id", target = "filiereId")
    @Mapping(source = "filiere.libelle", target = "filiereLibelle")
    @Mapping(source = "anneeScolaire.id", target = "anneeScolaireId")
    @Mapping(source = "anneeScolaire.libelle", target = "anneeScolaireLibelle")
    ClasseDto versDto(Classe classe);

    default ClasseHistoriqueDto versHistoriqueDto(RevisionHistorique<Classe> revision) {
        Classe entite = revision.entite();
        return new ClasseHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getLibelle(),
                entite.getSuffixe(),
                entite.getNiveau() != null ? entite.getNiveau().getId() : null,
                entite.getFiliere() != null ? entite.getFiliere().getId() : null,
                entite.getAnneeScolaire() != null ? entite.getAnneeScolaire().getId() : null,
                entite.getSiteId(),
                entite.getEffectifMax(),
                entite.isActif());
    }
}
