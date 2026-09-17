package tg.novadigital.edukeys.academique.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import tg.novadigital.edukeys.academique.domain.Niveau;
import tg.novadigital.edukeys.academique.web.NiveauDto;
import tg.novadigital.edukeys.academique.web.NiveauHistoriqueDto;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;

@Mapper(componentModel = "spring")
public interface NiveauMapper {

    @Mapping(source = "cycle.id", target = "cycleId")
    @Mapping(source = "cycle.libelle", target = "cycleLibelle")
    NiveauDto versDto(Niveau niveau);

    default NiveauHistoriqueDto versHistoriqueDto(RevisionHistorique<Niveau> revision) {
        Niveau entite = revision.entite();
        return new NiveauHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getLibelle(),
                entite.getCode(),
                entite.getRang(),
                entite.getCycle() != null ? entite.getCycle().getId() : null,
                entite.isActif());
    }
}
