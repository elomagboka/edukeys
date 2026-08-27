package tg.novadigital.edukeys.common.audit;

import java.time.Instant;

/**
 * Une révision consultable d'une entité auditée (T-06) : numéro de révision,
 * date, auteur (résolus depuis {@link RevisionAuteur}), type de révision et
 * l'état de l'entité à cette révision.
 */
public record RevisionHistorique<T>(long numero, Instant date, String auteur, TypeRevision type, T entite) {
}
