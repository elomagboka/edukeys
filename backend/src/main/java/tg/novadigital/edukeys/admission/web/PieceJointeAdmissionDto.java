package tg.novadigital.edukeys.admission.web;

import java.util.UUID;

public record PieceJointeAdmissionDto(UUID id, String typePiece, String nomOriginal, String typeMime, long tailleOctets) {
}
