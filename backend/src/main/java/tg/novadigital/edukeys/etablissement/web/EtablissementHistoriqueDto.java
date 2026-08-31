package tg.novadigital.edukeys.etablissement.web;

import java.time.Instant;
import java.util.UUID;

public record EtablissementHistoriqueDto(
        long numeroRevision,
        Instant date,
        String auteur,
        String typeRevision,
        UUID id,
        String code,
        String nom,
        String ville,
        String email,
        boolean actif) {
}
