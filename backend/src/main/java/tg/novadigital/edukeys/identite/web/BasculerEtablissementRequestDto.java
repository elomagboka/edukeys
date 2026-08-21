package tg.novadigital.edukeys.identite.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record BasculerEtablissementRequestDto(@NotNull UUID etablissementId) {
}
