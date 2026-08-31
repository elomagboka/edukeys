package tg.novadigital.edukeys.etablissement.web;

import java.util.UUID;

public record SiteDto(
        UUID id,
        UUID etablissementId,
        String code,
        String nom,
        boolean principal,
        String ville,
        String quartier,
        String adresseLigne,
        String telephone,
        boolean actif) {
}
