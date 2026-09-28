package tg.novadigital.edukeys.admission.domain;

/**
 * Statuts d'une demande d'admission. La machine à états (US-07) est portée
 * par {@link DemandeAdmission#changerStatut(StatutAdmission, String, java.util.UUID, java.time.Instant)} :
 * EN_ATTENTE peut basculer vers ACCEPTEE, REFUSEE ou LISTE_ATTENTE ;
 * LISTE_ATTENTE vers ACCEPTEE ou REFUSEE ; REFUSEE vers LISTE_ATTENTE ou
 * ACCEPTEE (un refus est corrigible, ex. clic erroné) ; ACCEPTEE et ANNULEE
 * sont les seuls statuts finaux. EN_ATTENTE n'est jamais une cible de
 * transition.
 */
public enum StatutAdmission {
    EN_ATTENTE,
    ACCEPTEE,
    REFUSEE,
    LISTE_ATTENTE,
    ANNULEE
}
