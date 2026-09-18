package tg.novadigital.edukeys.academique.web;

import java.math.BigDecimal;
import java.util.UUID;

public record AffectationMatiereDto(
        UUID id,
        RefNiveauDto niveau,
        RefFiliereDto filiere,
        BigDecimal coefficient,
        BigDecimal volumeHoraire,
        Boolean obligatoire) {
}
