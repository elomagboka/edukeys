-- US-05 (module academique) : periodes academiques (trimestres/semestres)
-- d'une annee scolaire. Creation pure, aucune suppression (ADR-0004).

CREATE TABLE periodes_academiques (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    annee_scolaire_id   UUID          NOT NULL REFERENCES annees_scolaires (id),
    libelle             VARCHAR(40)   NOT NULL,
    type                VARCHAR(20)   NOT NULL,
    ordre               INTEGER       NOT NULL,
    date_debut          DATE          NOT NULL,
    date_fin            DATE          NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    CONSTRAINT ck_periodes_academiques_dates CHECK (date_fin > date_debut),
    CONSTRAINT ck_periodes_academiques_ordre CHECK (ordre BETWEEN 1 AND 6)
);

-- Libelle et ordre uniques par annee scolaire parmi les periodes actives
-- (R5) : index partiels, une periode desactivee libere son libelle/ordre
-- (CLAUDE.md, regle 4).
CREATE UNIQUE INDEX uk_periodes_academiques_libelle_actif
    ON periodes_academiques (etablissement_id, annee_scolaire_id, libelle) WHERE actif = TRUE;
CREATE UNIQUE INDEX uk_periodes_academiques_ordre_actif
    ON periodes_academiques (etablissement_id, annee_scolaire_id, ordre) WHERE actif = TRUE;

-- Non-chevauchement des plages de dates entre periodes actives d'une meme
-- annee scolaire (R4) : garde-fou de correction, garanti par la base,
-- pas seulement par une verification applicative (meme motif que
-- ex_annees_scolaires_chevauchement, V9). Deux trimestres qui se recouvrent
-- restent possibles pour des annees scolaires differentes.
ALTER TABLE periodes_academiques
    ADD CONSTRAINT ex_periodes_academiques_chevauchement
    EXCLUDE USING gist (
        etablissement_id WITH =,
        annee_scolaire_id WITH =,
        daterange(date_debut, date_fin, '[]') WITH &&
    )
    WHERE (actif = TRUE);

CREATE INDEX idx_periodes_academiques_etablissement ON periodes_academiques (etablissement_id);
CREATE INDEX idx_periodes_academiques_annee_scolaire ON periodes_academiques (annee_scolaire_id);

CREATE TABLE periodes_academiques_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    annee_scolaire_id   UUID,
    libelle             VARCHAR(40),
    type                VARCHAR(20),
    ordre               INTEGER,
    date_debut          DATE,
    date_fin            DATE,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);
