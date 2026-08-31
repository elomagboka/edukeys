package tg.novadigital.edukeys.etablissement.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.etablissement.domain.Site;
import tg.novadigital.edukeys.etablissement.web.SiteDto;

@Mapper(componentModel = "spring")
public interface SiteMapper {

    SiteDto versDto(Site site);
}
