package tg.novadigital.edukeys.academique.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.academique.domain.AnneeScolaire;
import tg.novadigital.edukeys.academique.web.AnneeScolaireDto;
import tg.novadigital.edukeys.academique.web.AnneeScolaireHistoriqueDto;
import tg.novadigital.edukeys.common.audit.RevisionHistorique;

@Mapper(componentModel = "spring")
public interface AnneeScolaireMapper {

    AnneeScolaireDto versDto(AnneeScolaire anneeScolaire);

    default AnneeScolaireHistoriqueDto versHistoriqueDto(RevisionHistorique<AnneeScolaire> revision) {
        AnneeScolaire entite = revision.entite();
        return new AnneeScolaireHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getLibelle(),
                entite.getDateDebut(),
                entite.getDateFin(),
                entite.getStatut().name(),
                entite.isActif());
    }
}
