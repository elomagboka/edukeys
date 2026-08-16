package tg.novadigital.edukeys.common.exception;

/** Une règle métier empêche l'opération demandée, indépendamment de tout conflit de concurrence. */
public class RegleMetierViolee extends EdukeysException {

    public RegleMetierViolee(String message) {
        super(message);
    }
}
