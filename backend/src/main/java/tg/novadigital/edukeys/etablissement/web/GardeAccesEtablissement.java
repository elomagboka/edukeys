package tg.novadigital.edukeys.etablissement.web;

import java.util.UUID;

import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;
import tg.novadigital.edukeys.common.multietablissement.PerimetreEtablissement;

/**
 * R11 : un ADMIN n'accède qu'à son propre établissement. {@link
 * tg.novadigital.edukeys.etablissement.domain.Etablissement} n'étend pas
 * {@code EntiteEtablissement} (ADR-0002) — aucun filtre Hibernate ne borne
 * automatiquement sa lecture par identifiant — donc sans ce garde-fou
 * explicite, un ADMIN de l'établissement A pourrait consulter (ou modifier)
 * n'importe quel établissement B en passant simplement son identifiant.
 *
 * <p>ADMIN authentifié via JWT ouvre systématiquement un contexte
 * d'établissement ({@code ContexteEtablissementFilter}) : sa présence est
 * donc le signal utilisé ici pour distinguer ADMIN (contexte ouvert, borné)
 * de SUPER_ADMIN (généralement sans contexte ouvert, seul rôle à porter
 * {@code ETABLISSEMENT_CREER} et donc à agir sur plusieurs établissements).
 * {@code 404} et non {@code 403} : ne pas révéler à un ADMIN qu'un
 * établissement différent du sien existe.</p>
 */
public final class GardeAccesEtablissement {

    private GardeAccesEtablissement() {
    }

    public static void verifierAcces(UUID etablissementId) {
        ContexteEtablissement.courant()
                .map(PerimetreEtablissement::etablissementId)
                .filter(courant -> !courant.equals(etablissementId))
                .ifPresent(courant -> {
                    throw new RessourceIntrouvableException("Établissement introuvable.");
                });
    }
}
