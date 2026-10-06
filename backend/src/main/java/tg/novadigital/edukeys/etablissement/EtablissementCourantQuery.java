package tg.novadigital.edukeys.etablissement;

/**
 * Port exposé par {@code etablissement} au module {@code eleve} (US-08) : les paramètres de
 * l'établissement courant utiles à l'inscription. Aucune entité n'en sort (CLAUDE.md, règle 1).
 * Opère sur l'établissement du contexte courant ({@code ContexteEtablissement.exigerEtablissementId()}) ;
 * n'ouvre pas de portée.
 */
public interface EtablissementCourantQuery {

    ParametresInscription parametresInscription();

    /**
     * @param code code de l'établissement, préfixe du matricule ({@code CODE-AAAA-NNNNN})
     * @param fuseauHoraire identifiant de fuseau IANA (ex. {@code Africa/Lome}), pour « début de journée » de la rentrée
     */
    record ParametresInscription(String code, String fuseauHoraire) {
    }
}
