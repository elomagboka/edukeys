-- US-03 (module academique) : matieres et leurs affectations (niveau, filiere
-- optionnelle). Creation pure, aucune suppression (ADR-0004). Table calquee
-- sur filieres (sans cycle_id) ; une seule table de liaison
-- affectations_matieres (couple niveau/filiere, pas deux listes distinctes) :
-- une matiere existe independamment de ses affectations.

CREATE TABLE matieres (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    libelle             VARCHAR(80)   NOT NULL,
    code                VARCHAR(20),
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

-- filiere_id nullable : NULL = ce niveau, toutes filieres (ex. college).
CREATE TABLE affectations_matieres (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    matiere_id          UUID          NOT NULL REFERENCES matieres (id),
    niveau_id           UUID          NOT NULL REFERENCES niveaux (id),
    filiere_id          UUID          REFERENCES filieres (id),
    coefficient         NUMERIC(4,2),
    volume_horaire      NUMERIC(4,1),
    obligatoire         BOOLEAN,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    CONSTRAINT ck_affectations_matieres_coefficient CHECK (coefficient IS NULL OR coefficient > 0),
    CONSTRAINT ck_affectations_matieres_volume_horaire CHECK (volume_horaire IS NULL OR volume_horaire > 0)
);

-- Index uniques partiels WHERE actif = TRUE (regle 4) : un libelle/code
-- libere par desactivation redevient disponible.
CREATE UNIQUE INDEX uk_matieres_libelle_actif ON matieres (etablissement_id, libelle) WHERE actif = TRUE;
CREATE UNIQUE INDEX uk_matieres_code_actif ON matieres (etablissement_id, code) WHERE actif = TRUE AND code IS NOT NULL;

-- Deux index uniques distincts selon que filiere_id est renseignee ou non :
-- NULL n'est jamais egal a NULL dans une contrainte UNIQUE classique, donc
-- deux affectations "niveau seul" identiques ne seraient pas bloquees sans
-- l'index partiel dedie WHERE filiere_id IS NULL.
CREATE UNIQUE INDEX uk_affectations_matieres_actif
    ON affectations_matieres (etablissement_id, matiere_id, niveau_id, filiere_id)
    WHERE actif = TRUE AND filiere_id IS NOT NULL;
CREATE UNIQUE INDEX uk_affectations_matieres_niveau_actif
    ON affectations_matieres (etablissement_id, matiere_id, niveau_id)
    WHERE actif = TRUE AND filiere_id IS NULL;

CREATE INDEX idx_matieres_etablissement ON matieres (etablissement_id);
CREATE INDEX idx_affectations_matieres_etablissement ON affectations_matieres (etablissement_id);
CREATE INDEX idx_affectations_matieres_matiere ON affectations_matieres (matiere_id);
CREATE INDEX idx_affectations_matieres_niveau ON affectations_matieres (niveau_id);
CREATE INDEX idx_affectations_matieres_filiere ON affectations_matieres (filiere_id);

-- Tables d'historisation Envers, calquees sur filieres_aud : colonnes de FK
-- nullables et sans contrainte FK (Envers conserve des lignes pointant vers
-- des revisions, pas vers l'etat courant).
CREATE TABLE matieres_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    libelle             VARCHAR(80),
    code                VARCHAR(20),
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE affectations_matieres_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    matiere_id          UUID,
    niveau_id           UUID,
    filiere_id          UUID,
    coefficient         NUMERIC(4,2),
    volume_horaire      NUMERIC(4,1),
    obligatoire         BOOLEAN,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);
