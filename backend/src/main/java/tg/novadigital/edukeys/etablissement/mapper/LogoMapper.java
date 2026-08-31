package tg.novadigital.edukeys.etablissement.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.etablissement.domain.LogoEtablissement;
import tg.novadigital.edukeys.etablissement.web.LogoDto;

@Mapper(componentModel = "spring")
public interface LogoMapper {

    LogoDto versDto(LogoEtablissement logo);
}
