package tg.novadigital.edukeys.common.securite;

/**
 * Contrat minimal permettant à {@code common} de résoudre l'auteur d'audit
 * JPA sans dépendre du module {@code identite} (règle d'architecture n°1 :
 * aucune dépendance croisée entre modules métier — c'est {@code identite} qui
 * dépend de {@code common}, jamais l'inverse). Implémenté par
 * {@code UtilisateurPrincipal}.
 */
public interface PrincipalAuditable {

    String identifiantAudit();
}
