package tg.novadigital.edukeys.common.initialisation;

import java.util.List;

/**
 * Modèle brut du référentiel pédagogique type (US-00 : cycles, niveaux,
 * filières, matières du système togolais). Structure de données pure, jamais
 * d'entité JPA ici : {@code common} n'importe aucun module métier
 * (CLAUDE.md, règle 1), c'est {@link InitialisateurReferentiel} — implémenté
 * par le module {@code academique} — qui traduit ce modèle en entités.
 */
public record ReferentielType(
        List<Cycle> cycles,
        List<Niveau> niveaux,
        List<Filiere> filieres,
        List<Matiere> matieres) {

    public record Cycle(String code, String libelle, int ordre) {
    }

    public record Niveau(String code, String libelle, String codeCycle, int ordre) {
    }

    /** {@code codeNiveau} référence un {@link Niveau#code} existant — vérifié par {@link ChargeurReferentielType}. */
    public record Filiere(String code, String libelle, String codeNiveau) {
    }

    public record Matiere(String code, String libelle) {
    }
}
