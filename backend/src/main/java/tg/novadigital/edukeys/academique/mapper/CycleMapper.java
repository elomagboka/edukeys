package tg.novadigital.edukeys.academique.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.academique.domain.Cycle;
import tg.novadigital.edukeys.academique.web.CycleDto;
import tg.novadigital.edukeys.academique.web.CycleHistoriqueDto;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;

@Mapper(componentModel = "spring")
public interface CycleMapper {

    CycleDto versDto(Cycle cycle);

    default CycleHistoriqueDto versHistoriqueDto(RevisionHistorique<Cycle> revision) {
        Cycle entite = revision.entite();
        return new CycleHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getLibelle(),
                entite.getCode(),
                entite.getRang(),
                entite.isActif());
    }
}
