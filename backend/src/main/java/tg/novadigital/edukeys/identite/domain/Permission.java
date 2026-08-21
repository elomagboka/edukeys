package tg.novadigital.edukeys.identite.domain;

/**
 * Catalogue fermé des permissions élémentaires d'Edukeys. Une permission
 * n'existe qu'attachée à un ou plusieurs {@link RoleCode} — voir
 * {@link RoleCode#getPermissions()}. Énumération plutôt qu'entité JPA
 * (correction T-04, lot 1 n°2) : le référentiel RBAC ne change jamais sans
 * déploiement de toute façon (il est câblé dans le code), donc le modéliser
 * en base n'achetait que le coût d'une requête en base à chaque requête HTTP
 * pour résoudre les permissions effectives d'un rôle — précisément ce que la
 * spec T-04 voulait éviter.
 */
public enum Permission {
    ETABLISSEMENT_GERER("Gérer un établissement"),
    UTILISATEUR_GERER("Gérer les utilisateurs de son établissement"),
    /**
     * Distincte de {@link #UTILISATEUR_GERER} : porte sur les comptes de
     * TOUS les établissements (endpoint {@code GET /api/v1/utilisateurs}).
     * Réservée à {@code SUPER_ADMIN} — {@code ADMIN} porte {@code UTILISATEUR_GERER}
     * mais reste borné à son établissement (ADR-0002 §5). Les confondre a déjà
     * causé une fuite inter-établissement (relecture T-04, repasse n°2) :
     * ADMIN portait aussi UTILISATEUR_GERER et obtenait donc la liste globale.
     */
    UTILISATEUR_GERER_PLATEFORME("Gérer les utilisateurs de tous les établissements"),
    NOTE_SAISIR("Saisir des notes"),
    DEVOIR_CREER("Créer un devoir"),
    ENFANT_CONSULTER("Consulter le dossier enfant"),
    BULLETIN_CONSULTER("Consulter un bulletin");

    private final String libelle;

    Permission(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
