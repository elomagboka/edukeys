package tg.novadigital.edukeys.identite.web;

/** Réponse de {@code POST /api/v1/utilisateurs/{id}/mot-de-passe-temporaire} : retourné une seule fois. */
public record MotDePasseTemporaireDto(String motDePasseTemporaire) {
}
