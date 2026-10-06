package tg.novadigital.edukeys.eleve.web;

import java.util.UUID;

/** Référence légère (identifiant + libellé) vers une classe, un niveau, une filière ou une année. */
public record ReferenceDto(UUID id, String libelle) {
}
