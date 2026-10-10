package tg.novadigital.edukeys.common.repository;

/**
 * Retrouve le nom de la contrainte de base violée derrière une exception de persistance. Le flush
 * explicite passe par l'{@code EntityManager} brut (pas de traduction Spring) : l'exception est un
 * {@code PersistenceException} Hibernate, ou un {@code DataIntegrityViolationException} si elle vient
 * d'un repository ; dans les deux cas la cause porte un {@code ConstraintViolationException} Hibernate.
 */
public final class ContrainteViolee {

    private ContrainteViolee() {
    }

    /** @return le nom de la contrainte (ou de l'index unique) violée, ou {@code null} si aucune violation n'est en cause */
    public static String nom(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
