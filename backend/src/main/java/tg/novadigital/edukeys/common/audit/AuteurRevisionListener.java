package tg.novadigital.edukeys.common.audit;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import tg.novadigital.edukeys.common.securite.PrincipalAuditable;

/**
 * Résout l'auteur d'une révision Envers depuis le contexte de sécurité, avec
 * repli {@code "system"} pour les traitements sans authentification —
 * exactement la même règle que {@code JpaAuditingConfig} pour
 * {@code creePar}/{@code modifiePar} (T-06 : les deux mécanismes d'audit
 * doivent désigner le même auteur pour une même écriture).
 */
public class AuteurRevisionListener implements RevisionListener {

    private static final String AUTEUR_SYSTEME = "system";

    @Override
    public void newRevision(Object revisionEntity) {
        RevisionAuteur revision = (RevisionAuteur) revisionEntity;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PrincipalAuditable principal) {
            revision.setAuteur(principal.identifiantAudit());
        } else {
            revision.setAuteur(AUTEUR_SYSTEME);
        }
    }
}
