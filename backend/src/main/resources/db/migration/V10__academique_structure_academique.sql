-- US-02 (module academique) : structure academique (cycles, niveaux,
-- filieres, classes). Creation pure, aucune suppression (ADR-0004).
-- Hierarchie D1 : Cycle > Niveau > Classe. Filiere rattachee au cycle,
-- optionnelle sur la classe (D4). Cycle/niveau/filiere = referentiel perenne,
-- sans annee_scolaire_id (D2) ; classe rattachee a une annee scolaire.

CREATE TABLE cycles (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    libelle             VARCHAR(60)   NOT NULL,
    code                VARCHAR(20),
    rang                INTEGER       NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    CONSTRAINT ck_cycles_rang CHECK (rang > 0)
);

CREATE TABLE niveaux (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    libelle             VARCHAR(60)   NOT NULL,
    code                VARCHAR(20),
    rang                INTEGER       NOT NULL,
    cycle_id            UUID          NOT NULL REFERENCES cycles (id),
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    CONSTRAINT ck_niveaux_rang CHECK (rang > 0)
);

CREATE TABLE filieres (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    libelle             VARCHAR(80)   NOT NULL,
    code                VARCHAR(20),
    cycle_id            UUID          REFERENCES cycles (id),
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

CREATE TABLE classes (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    libelle             VARCHAR(60)   NOT NULL,
    suffixe             VARCHAR(10),
    niveau_id           UUID          NOT NULL REFERENCES niveaux (id),
    filiere_id          UUID          REFERENCES filieres (id),
    annee_scolaire_id   UUID          NOT NULL REFERENCES annees_scolaires (id),
    site_id             UUID          NOT NULL REFERENCES sites (id),
    effectif_max        INTEGER,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    CONSTRAINT ck_classes_effectif_max CHECK (effectif_max IS NULL OR effectif_max > 0)
);

-- Index uniques partiels WHERE actif = TRUE (regle 4) : un libelle/rang/code
-- libere par desactivation redevient disponible.
CREATE UNIQUE INDEX uk_cycles_libelle_actif ON cycles (etablissement_id, libelle) WHERE actif = TRUE;
CREATE UNIQUE INDEX uk_cycles_rang_actif ON cycles (etablissement_id, rang) WHERE actif = TRUE;
CREATE UNIQUE INDEX uk_cycles_code_actif ON cycles (etablissement_id, code) WHERE actif = TRUE AND code IS NOT NULL;

CREATE UNIQUE INDEX uk_niveaux_libelle_actif ON niveaux (etablissement_id, libelle) WHERE actif = TRUE;
CREATE UNIQUE INDEX uk_niveaux_rang_actif ON niveaux (etablissement_id, rang) WHERE actif = TRUE;
CREATE UNIQUE INDEX uk_niveaux_code_actif ON niveaux (etablissement_id, code) WHERE actif = TRUE AND code IS NOT NULL;

CREATE UNIQUE INDEX uk_filieres_libelle_actif ON filieres (etablissement_id, libelle) WHERE actif = TRUE;
CREATE UNIQUE INDEX uk_filieres_code_actif ON filieres (etablissement_id, code) WHERE actif = TRUE AND code IS NOT NULL;

-- La cle inclut l'annee scolaire (D2) : sans elle, impossible de creer la
-- "6eme A" de l'annee suivante tant que celle de l'annee en cours est active.
CREATE UNIQUE INDEX uk_classes_libelle_actif ON classes (etablissement_id, annee_scolaire_id, libelle) WHERE actif = TRUE;

CREATE INDEX idx_cycles_etablissement ON cycles (etablissement_id);
CREATE INDEX idx_niveaux_etablissement ON niveaux (etablissement_id);
CREATE INDEX idx_filieres_etablissement ON filieres (etablissement_id);
CREATE INDEX idx_classes_etablissement ON classes (etablissement_id);
CREATE INDEX idx_niveaux_cycle ON niveaux (cycle_id);
CREATE INDEX idx_classes_annee ON classes (annee_scolaire_id);
CREATE INDEX idx_classes_niveau ON classes (niveau_id);
CREATE INDEX idx_classes_site ON classes (site_id);

-- Tables d'historisation Envers, calquees sur annees_scolaires_aud : colonnes
-- de FK nullables et sans contrainte FK (Envers conserve des lignes pointant
-- vers des revisions, pas vers l'etat courant).
CREATE TABLE cycles_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    libelle             VARCHAR(60),
    code                VARCHAR(20),
    rang                INTEGER,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE niveaux_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    libelle             VARCHAR(60),
    code                VARCHAR(20),
    rang                INTEGER,
    cycle_id            UUID,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE filieres_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    libelle             VARCHAR(80),
    code                VARCHAR(20),
    cycle_id            UUID,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE classes_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    libelle             VARCHAR(60),
    suffixe             VARCHAR(10),
    niveau_id           UUID,
    filiere_id          UUID,
    annee_scolaire_id   UUID,
    site_id             UUID,
    effectif_max        INTEGER,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);
