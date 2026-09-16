package tg.novadigital.edukeys.academique.service;

/**
 * Normalisation partagée par les quatre services de la structure académique
 * (US-02, R1) : libellé {@code trim()}, code normalisé en majuscules avec
 * chaîne vide stockée {@code null} (sinon l'index partiel {@code code IS NOT
 * NULL} verrouille la chaîne vide pour tout l'établissement).
 */
final class UtilitairesAcademique {

    private UtilitairesAcademique() {
    }

    static String normaliserLibelle(String libelle) {
        return libelle == null ? null : libelle.trim();
    }

    static String normaliserCode(String code) {
        if (code == null) {
            return null;
        }
        String normalise = code.trim().toUpperCase();
        return normalise.isEmpty() ? null : normalise;
    }
}
