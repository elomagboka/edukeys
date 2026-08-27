package tg.novadigital.edukeys.common.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Socle commun à toutes les entités métier : identifiant UUID v7 (ordonné
 * dans le temps, imprévisible de l'extérieur), audit JPA et désactivation
 * logique. Voir les règles d'architecture non négociables dans CLAUDE.md.
 *
 * <p><b>{@code @Audited} (T-06) ne se propage pas aux sous-classes</b> avec la
 * version d'Hibernate Envers embarquée par Spring Boot 3.5.16 : l'annotation
 * posée ici documente l'intention (toute entité métier est historisée), mais
 * chaque entité concrète doit répéter {@code @Audited} sur sa propre classe
 * (voir {@code Etablissement} ou {@code DemoEntite}). {@link
 * tg.novadigital.edukeys.common.audit.VerificateurAuditEnvers} fait échouer le
 * démarrage si une entité l'a oublié — ne pas compter sur la vigilance en
 * revue pour ce piège, déjà rencontré sur les cinq entités existantes.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Audited
public abstract class BaseEntity implements EntiteDesactivable {

    @Id
    @UuidGenerator(algorithm = UuidV7Generator.class)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private boolean actif = true;

    private Instant dateDesactivation;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant dateCreation;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant dateModification;

    @CreatedBy
    @Column(updatable = false)
    private String creePar;

    @LastModifiedBy
    private String modifiePar;

    public UUID getId() {
        return id;
    }

    @Override
    public boolean isActif() {
        return actif;
    }

    @Override
    public Instant getDateDesactivation() {
        return dateDesactivation;
    }

    @Override
    public void desactiver() {
        this.actif = false;
        this.dateDesactivation = Instant.now();
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateModification() {
        return dateModification;
    }

    public String getCreePar() {
        return creePar;
    }

    public String getModifiePar() {
        return modifiePar;
    }
}
