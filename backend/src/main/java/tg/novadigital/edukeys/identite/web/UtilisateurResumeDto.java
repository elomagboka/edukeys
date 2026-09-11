package tg.novadigital.edukeys.identite.web;

import java.util.UUID;

import tg.novadigital.edukeys.identite.domain.Utilisateur;

/**
 * Résumé d'un compte utilisateur, jamais l'entité JPA elle-même
 * (CLAUDE.md, règle 7).
 */
public record UtilisateurResumeDto(
        UUID id, String email, String nomComplet, boolean superAdmin, boolean actif, boolean motDePasseAChanger) {

    public static UtilisateurResumeDto depuis(Utilisateur utilisateur) {
        return new UtilisateurResumeDto(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getNomComplet(),
                utilisateur.isSuperAdmin(),
                utilisateur.isActif(),
                utilisateur.isMotDePasseAChanger());
    }
}
