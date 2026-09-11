package tg.novadigital.edukeys.etablissement.web;

/**
 * Réponse de {@code POST /api/v1/etablissements} : l'établissement créé et le
 * mot de passe temporaire de son premier administrateur (US-04 §3), retourné
 * une seule fois — jamais relogué, jamais renvoyé par une lecture ultérieure.
 */
public record EtablissementCreeDto(EtablissementDto etablissement, String motDePasseTemporaireAdmin) {
}
