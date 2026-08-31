package tg.novadigital.edukeys.etablissement.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.common.audit.RevisionHistorique;
import tg.novadigital.edukeys.etablissement.domain.Etablissement;
import tg.novadigital.edukeys.etablissement.web.EtablissementDto;
import tg.novadigital.edukeys.etablissement.web.EtablissementHistoriqueDto;
import tg.novadigital.edukeys.etablissement.web.EtablissementResumeDto;

@Mapper(componentModel = "spring")
public interface EtablissementMapper {

    EtablissementDto versDto(Etablissement etablissement);

    default EtablissementResumeDto versResumeDto(Etablissement etablissement) {
        return new EtablissementResumeDto(
                etablissement.getId(),
                etablissement.getCode(),
                etablissement.getNom(),
                etablissement.getSigle(),
                etablissement.getTypeEtablissement(),
                etablissement.getVille(),
                etablissement.isActif(),
                etablissement.getNombreSitesActifs());
    }

    default EtablissementHistoriqueDto versHistoriqueDto(RevisionHistorique<Etablissement> revision) {
        Etablissement entite = revision.entite();
        return new EtablissementHistoriqueDto(
                revision.numero(),
                revision.date(),
                revision.auteur(),
                revision.type().name(),
                entite.getId(),
                entite.getCode(),
                entite.getNom(),
                entite.getVille(),
                entite.getEmail(),
                entite.isActif());
    }
}
