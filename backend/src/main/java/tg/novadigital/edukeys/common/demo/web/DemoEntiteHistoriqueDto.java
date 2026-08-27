package tg.novadigital.edukeys.common.demo.web;

import java.time.Instant;
import java.util.UUID;

public record DemoEntiteHistoriqueDto(
        long revision,
        Instant date,
        String auteur,
        String typeRevision,
        UUID id,
        String libelle,
        String categorie,
        Integer quantite,
        boolean actif) {
}
