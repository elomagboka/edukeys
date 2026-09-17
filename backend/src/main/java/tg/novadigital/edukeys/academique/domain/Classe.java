package tg.novadigital.edukeys.academique.domain;

import java.util.UUID;

import org.hibernate.envers.Audited;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tg.novadigital.edukeys.common.domain.EntiteEtablissement;

/**
 * Classe d'une année scolaire donnée (US-02, D2) : « 6ème A 2025-2026 » et
 * « 6ème A 2026-2027 » ne sont pas le même objet. Rattachée à
 * {@link AnneeScolaire} en référence directe (même module, D2) et à un
 * {@code site_id} <strong>scalaire</strong> (D3) — jamais une relation
 * {@code @ManyToOne} vers {@code Site} : le module {@code academique}
 * n'importe jamais le module {@code etablissement} (CLAUDE.md, règle 1).
 *
 * <p>{@code @Audited} répété explicitement (voir {@code Cycle}).</p>
 */
@Entity
@Table(name = "classes")
@Audited
public class Classe extends EntiteEtablissement {

    @Column(nullable = false, length = 60)
    private String libelle;

    @Column(length = 10)
    private String suffixe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "niveau_id", nullable = false)
    private Niveau niveau;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "filiere_id")
    private Filiere filiere;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annee_scolaire_id", nullable = false, updatable = false)
    private AnneeScolaire anneeScolaire;

    /** Scalaire, jamais {@code @ManyToOne} (D3) : hors filtre Hibernate, FK en base uniquement. */
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "effectif_max")
    private Integer effectifMax;

    protected Classe() {
    }

    public Classe(UUID etablissementId, String libelle, String suffixe, Niveau niveau, Filiere filiere,
                  AnneeScolaire anneeScolaire, UUID siteId, Integer effectifMax) {
        super(etablissementId);
        this.libelle = libelle;
        this.suffixe = suffixe;
        this.niveau = niveau;
        this.filiere = filiere;
        this.anneeScolaire = anneeScolaire;
        this.siteId = siteId;
        this.effectifMax = effectifMax;
    }

    /** R12 : ne touche jamais {@code anneeScolaire} — une classe ne change jamais d'année après création. */
    public void modifier(String libelle, String suffixe, Niveau niveau, Filiere filiere, UUID siteId, Integer effectifMax) {
        this.libelle = libelle;
        this.suffixe = suffixe;
        this.niveau = niveau;
        this.filiere = filiere;
        this.siteId = siteId;
        this.effectifMax = effectifMax;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getSuffixe() {
        return suffixe;
    }

    public Niveau getNiveau() {
        return niveau;
    }

    public Filiere getFiliere() {
        return filiere;
    }

    public AnneeScolaire getAnneeScolaire() {
        return anneeScolaire;
    }

    public UUID getSiteId() {
        return siteId;
    }

    public Integer getEffectifMax() {
        return effectifMax;
    }
}
