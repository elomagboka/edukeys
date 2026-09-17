package tg.novadigital.edukeys.academique.service;

import org.springframework.dao.DataIntegrityViolationException;

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

    /**
     * Nom de la contrainte (index unique partiel ou {@code CHECK}) à l'origine
     * d'une {@link DataIntegrityViolationException}, quand le pilote JDBC le
     * fournit (point IMPORTANT n°3 de la revue US-02) : {@code cycles}, {@code
     * niveaux}, {@code filieres} et {@code classes} portent plusieurs
     * contraintes distinctes (libellé, rang, code, effectif) et une seule
     * traduction générique vers "libellé dupliqué" masque la vraie cause.
     *
     * <p>{@code null} si la cause n'est pas une
     * {@code org.hibernate.exception.ConstraintViolationException} ou si le
     * pilote n'a pas remonté de nom de contrainte : l'appelant ne doit alors
     * <strong>pas</strong> deviner un code d'erreur, au risque de mentir sur
     * la cause réelle du conflit.</p>
     */
    static String nomContrainteViolee(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
