package tg.novadigital.edukeys.admission.mapper;

import org.mapstruct.Mapper;

import tg.novadigital.edukeys.admission.domain.PieceJointeAdmission;
import tg.novadigital.edukeys.admission.web.PieceJointeAdmissionDto;

@Mapper(componentModel = "spring")
public interface PieceJointeAdmissionMapper {

    default PieceJointeAdmissionDto versDto(PieceJointeAdmission piece) {
        if (piece == null) {
            return null;
        }
        return new PieceJointeAdmissionDto(
                piece.getId(), piece.getTypePiece().name(), piece.getNomOriginal(), piece.getTypeMime(), piece.getTailleOctets());
    }
}
