package tg.novadigital.edukeys.etablissement.domain;

/**
 * Type pédagogique d'un établissement (US-00). Stocké en {@code STRING}
 * (jamais {@code ORDINAL} : un enum {@code ORDINAL} rendrait toute
 * réorganisation de cette liste destructive pour les lignes déjà en base).
 */
public enum TypeEtablissement {
    PRESCOLAIRE,
    PRIMAIRE,
    COLLEGE,
    LYCEE,
    COMPLEXE
}
