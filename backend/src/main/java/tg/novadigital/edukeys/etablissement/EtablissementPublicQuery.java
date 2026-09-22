package tg.novadigital.edukeys.etablissement;

import java.util.Optional;
import java.util.UUID;

/**
 * Résolution publique d'un établissement par son code (US-06, pré-inscription
 * en ligne) : seul point d'entrée exposé au module {@code admission}
 * (CLAUDE.md, règle 1). Aucune entité {@link tg.novadigital.edukeys.etablissement.domain.Etablissement}
 * ne sort de ce module — un record en sortie, jamais l'entité.
 */
public interface EtablissementPublicQuery {

    Optional<EtablissementPublic> resoudreParCode(String code);

    record EtablissementPublic(UUID id, String nom, String logoUrl, boolean admissionsOuvertes) {
    }
}
