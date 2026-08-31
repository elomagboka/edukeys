package tg.novadigital.edukeys.etablissement.service;

import java.util.Locale;
import java.util.UUID;

import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.etablissement.repository.EtablissementRepository;

/**
 * Utilitaires factorisés (durcissement post-revue T-10) : {@code normaliserCode}
 * et la vérification d'existence d'un établissement étaient dupliqués à
 * l'identique dans {@code EtablissementService} et {@code SiteService}.
 * Reste dans le module {@code etablissement} (jamais dans {@code common}) :
 * {@code normaliserCode} est un détail propre à ce module, pas une notion
 * générique partagée par le reste de l'application (CLAUDE.md, règle 1).
 */
final class UtilitairesEtablissement {

    private UtilitairesEtablissement() {
    }

    /** Code établissement/site normalisé : majuscules, espaces retirés. */
    static String normaliserCode(String code) {
        return code.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    static void verifierEtablissementExiste(EtablissementRepository etablissementRepository, UUID etablissementId) {
        if (!etablissementRepository.existsById(etablissementId)) {
            throw new RessourceIntrouvableException("Établissement introuvable.");
        }
    }
}
