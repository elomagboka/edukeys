package tg.novadigital.edukeys.etablissement;

import java.util.UUID;

/**
 * Seul point d'entrée public du module {@code etablissement} pour le module
 * {@code academique} (US-02) : aucun import de
 * {@link tg.novadigital.edukeys.etablissement.domain.Site} hors de ce module,
 * jamais (CLAUDE.md, règle 1). Sur le modèle d'{@code AnneeScolaireQuery}.
 *
 * <p>{@code site_id} est la seule valeur du corps d'une requête HTTP
 * d'{@code academique} qui n'est <strong>pas</strong> protégée par le filtre
 * Hibernate multi-établissement — il n'entre jamais dans ce filtre (ADR-0005).
 * {@link #existeDansEtablissementCourant(UUID)} est donc le seul rempart
 * contre un {@code site_id} appartenant à un autre établissement, glissé dans
 * une requête {@code POST /classes}.</p>
 *
 * <p>Opère sur l'établissement du contexte courant ({@code ContexteEtablissement}) :
 * aucune méthode ne prend {@code etablissementId} en paramètre.</p>
 */
public interface SiteQuery {

    /** Vrai si {@code siteId} désigne un site actif de l'établissement courant. */
    boolean existeDansEtablissementCourant(UUID siteId);

    /** Identifiant du site principal actif de l'établissement courant. */
    UUID idSitePrincipal();
}
