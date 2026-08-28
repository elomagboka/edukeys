package tg.novadigital.edukeys.common.audit;

import java.io.Serializable;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Remplace la {@code DefaultRevisionEntity} d'Envers pour porter l'auteur de
 * chaque révision (T-06) : {@link AuteurRevisionListener} le résout depuis le
 * contexte de sécurité, avec le même repli {@code "system"} que
 * {@code JpaAuditingConfig} pour {@code creePar}/{@code modifiePar}.
 */
@Entity
@Table(name = "revisions")
@RevisionEntity(AuteurRevisionListener.class)
public class RevisionAuteur implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_revisions")
    @SequenceGenerator(name = "seq_revisions", sequenceName = "seq_revisions", allocationSize = 1)
    @RevisionNumber
    @Column(name = "rev")
    private long rev;

    @RevisionTimestamp
    @Column(name = "revtstmp", nullable = false)
    private long timestamp;

    @Column(name = "auteur")
    private String auteur;

    public long getRev() {
        return rev;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getAuteur() {
        return auteur;
    }

    public void setAuteur(String auteur) {
        this.auteur = auteur;
    }
}
