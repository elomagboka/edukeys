package tg.novadigital.edukeys.common.demo.mapper;

import tg.novadigital.edukeys.common.demo.domain.DemoEntite;
import tg.novadigital.edukeys.common.demo.web.DemoEntiteDto;

/**
 * Mapper manuel (pas de MapStruct pour cette entité de démonstration
 * interne, afin de ne pas ajouter de dépendance pour du code non métier).
 */
public final class DemoEntiteMapper {

    private DemoEntiteMapper() {
    }

    public static DemoEntiteDto versDto(DemoEntite entite) {
        return new DemoEntiteDto(
                entite.getId(),
                entite.getLibelle(),
                entite.getCategorie(),
                entite.getQuantite(),
                entite.isActif());
    }
}
