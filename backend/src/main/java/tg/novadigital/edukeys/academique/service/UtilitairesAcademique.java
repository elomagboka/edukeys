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
     *
     * <p>Le paramètre est une {@link RuntimeException} et non une
     * {@link DataIntegrityViolationException} : une violation de contrainte
     * d'exclusion remonte en {@code org.hibernate.exception.ConstraintViolationException}
     * brute, sans passer par la traduction Spring. La chaîne est donc parcourue
     * depuis l'exception elle-même, pas depuis sa cause.</p>
     *
     * <p>Repli sur le message pour les contraintes d'exclusion {@code EXCLUDE
     * USING gist} ({@code ex_annees_scolaires_chevauchement} en US-01,
     * {@code ex_periodes_academiques_chevauchement} en US-05) :
     * {@code getConstraintName()} renvoie {@code null} pour ces contraintes,
     * l'extracteur PostgreSQL d'Hibernate ne reconnaissant que {@code unique},
     * {@code foreign key}, {@code check} et {@code not null}. Sans ce repli, un
     * chevauchement arbitré par la base remonte en 500 au lieu de 409.</p>
     */
    static String nomContrainteViolee(RuntimeException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation) {
                String nom = constraintViolation.getConstraintName();
                return nom != null ? nom : nomContrainteDansMessage(constraintViolation);
            }
            cause = cause.getCause();
        }
        return null;
    }

    private static String nomContrainteDansMessage(Throwable cause) {
        for (Throwable courant = cause; courant != null; courant = courant.getCause()) {
            String message = courant.getMessage();
            if (message == null) {
                continue;
            }
            java.util.regex.Matcher correspondance = CONTRAINTE_EXCLUSION.matcher(message);
            if (correspondance.find()) {
                return correspondance.group(1);
            }
        }
        return null;
    }

    private static final java.util.regex.Pattern CONTRAINTE_EXCLUSION =
            java.util.regex.Pattern.compile("violates exclusion constraint \"([^\"]+)\"");
}
