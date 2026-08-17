package tg.novadigital.edukeys.common.demo.web;

import java.util.UUID;

public record DemoEntiteDto(UUID id, String libelle, String categorie, Integer quantite, boolean actif) {
}
