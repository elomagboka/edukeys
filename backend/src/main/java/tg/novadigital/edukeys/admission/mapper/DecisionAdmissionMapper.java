package tg.novadigital.edukeys.admission.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import tg.novadigital.edukeys.admission.domain.DecisionAdmission;
import tg.novadigital.edukeys.admission.web.DecisionAdmissionDto;

@Mapper(componentModel = "spring")
public interface DecisionAdmissionMapper {

    @Mapping(target = "decideParId", source = "decidePar")
    DecisionAdmissionDto versDto(DecisionAdmission decision);
}
