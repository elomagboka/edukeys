package tg.novadigital.edukeys.academique.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import tg.novadigital.edukeys.academique.domain.Filiere;
import tg.novadigital.edukeys.academique.web.FiliereDto;
import tg.novadigital.edukeys.academique.web.FiliereHistoriqueDto;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;

@Mapper(componentModel = "spring")
public interface FiliereMapper {

    @Mapping(source = "cycle.id", target = "cycleId")
    @Mapping(source = "cycle.libelle", target = "cycleLibelle")
    FiliereDto versDto(Filiere filiere);

    default FiliereHistoriqueDto versHistoriqueDto(RevisionHistorique<Filiere> revision) {
        Filiere entite = revision.entite();
        return new FiliereHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getLibelle(),
                entite.getCode(),
                entite.getCycle() != null ? entite.getCycle().getId() : null,
                entite.isActif());
    }
}
