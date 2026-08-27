package tg.novadigital.edukeys.common.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;
import tg.novadigital.edukeys.common.exception.RessourceIntrouvableException;
import tg.novadigital.edukeys.common.multietablissement.ContexteEtablissement;

/**
 * Consultation générique de l'historique Envers d'une entité (T-06) :
 * réutilisable par tout module métier via son propre contrôleur, sans que
 * {@code common} n'importe jamais une entité métier (CLAUDE.md, règle 1) —
 * c'est l'appelant qui fournit sa {@code Class<T>}.
 *
 * <p><b>Angle mort comblé ici, pas laissé pour plus tard</b> (voir JOURNAL,
 * T-05) : les tables {@code _aud} d'Envers ne sont <em>pas</em> soumises au
 * filtre Hibernate multi-établissement. Sans garde explicite, l'historique
 * d'une entité {@link EntiteEtablissement} de l'établissement B serait
 * consultable par un utilisateur de l'établissement A par simple appel de cet
 * endpoint générique avec l'identifiant de l'entité. {@code etablissementId}
 * étant {@code updatable = false} (immuable sur toute la vie de l'entité), une
 * seule révision suffit à trancher.</p>
 */
@Service
public class HistoriqueService {

    private final EntityManager entityManager;

    public HistoriqueService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * {@code readOnly = true} : sans transaction, {@code open-in-view: false}
     * (application.yml) laisse l'{@code EntityManager} injecté sans session
     * Hibernate liée, et {@code AuditReader} échoue avec
     * {@code IllegalStateException: The associated entity manager is closed!}.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T> List<RevisionHistorique<T>> historique(Class<T> typeEntite, UUID id) {
        AuditReader lecteur = AuditReaderFactory.get(entityManager);

        List<Object[]> lignes = lecteur.createQuery()
                .forRevisionsOfEntity(typeEntite, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<RevisionHistorique<T>> revisions = lignes.stream()
                .map(ligne -> versRevisionHistorique((T) ligne[0], (RevisionAuteur) ligne[1], (RevisionType) ligne[2]))
                .toList();

        verifierAppartenanceEtablissementCourant(typeEntite, revisions);

        return revisions;
    }

    private <T> RevisionHistorique<T> versRevisionHistorique(T entite, RevisionAuteur revision, RevisionType typeRevision) {
        return new RevisionHistorique<>(
                revision.getRev(),
                Instant.ofEpochMilli(revision.getTimestamp()),
                revision.getAuteur(),
                TypeRevision.depuis(typeRevision),
                entite);
    }

    /**
     * Ne s'applique qu'aux entités {@link EntiteEtablissement} : {@code Etablissement}
     * et {@code Utilisateur} restent hors périmètre du filtre par conception
     * (ADR-0002), leur historique n'a donc pas à être cadré ici.
     */
    private <T> void verifierAppartenanceEtablissementCourant(Class<T> typeEntite, List<RevisionHistorique<T>> revisions) {
        if (!EntiteEtablissement.class.isAssignableFrom(typeEntite) || revisions.isEmpty()) {
            return;
        }
        UUID etablissementCourant = ContexteEtablissement.exigerEtablissementId();
        UUID etablissementDeLEntite = ((EntiteEtablissement) revisions.get(0).entite()).getEtablissementId();
        if (!etablissementCourant.equals(etablissementDeLEntite)) {
            throw new RessourceIntrouvableException("Historique introuvable.");
        }
    }
}
