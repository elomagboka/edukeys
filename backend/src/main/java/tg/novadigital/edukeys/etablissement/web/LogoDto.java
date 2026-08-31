package tg.novadigital.edukeys.etablissement.web;

import java.util.UUID;

public record LogoDto(
        UUID id,
        String nomFichier,
        String typeMime,
        int tailleOctets,
        String empreinteSha256) {
}
