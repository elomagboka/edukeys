package tg.novadigital.edukeys.admission.domain;

/**
 * Statuts d'une demande d'admission. La machine à états (US-07) est portée
 * par {@link DemandeAdmission#changerStatut(StatutAdmission)} : EN_ATTENTE
 * peut basculer vers ACCEPTEE, REFUSEE ou LISTE_ATTENTE ; LISTE_ATTENTE vers
 * ACCEPTEE ou REFUSEE ; ACCEPTEE et REFUSEE sont finales. ANNULEE existe pour
 * un usage futur (US-07) mais n'est atteignable par aucune transition définie
 * ici.
 */
public enum StatutAdmission {
    EN_ATTENTE,
    ACCEPTEE,
    REFUSEE,
    LISTE_ATTENTE,
    ANNULEE
}
