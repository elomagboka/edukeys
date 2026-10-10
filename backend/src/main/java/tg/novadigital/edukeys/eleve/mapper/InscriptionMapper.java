package tg.novadigital.edukeys.eleve.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.eleve.service.CommandeInscription;
import tg.novadigital.edukeys.eleve.service.ResultatInscription;
import tg.novadigital.edukeys.eleve.web.CompteEleveDto;
import tg.novadigital.edukeys.eleve.web.InscrireEleveRequestDto;
import tg.novadigital.edukeys.eleve.web.InscriptionCreeeDto;
import tg.novadigital.edukeys.eleve.web.ReferenceDto;

/** DTO <-> modèle de service (CLAUDE.md, règle 7). Le service ne voit jamais un DTO de {@code web}. */
@Mapper(componentModel = "spring")
public interface InscriptionMapper {

    CommandeInscription versCommande(InscrireEleveRequestDto dto);

    InscriptionCreeeDto versDto(ResultatInscription resultat);

    ReferenceDto versDto(ResultatInscription.Reference reference);

    CompteEleveDto versDto(ResultatInscription.CompteEleve compte);
}
