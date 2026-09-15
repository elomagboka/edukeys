-- US-01 (module academique) : annees scolaires. Premiere migration du
-- module academique. Creation pure, aucune suppression (ADR-0004).

-- btree_gist : premiere instruction (DELTA 2), necessaire a la contrainte
-- d'exclusion ci-dessous. Supporte par Render Postgres 13+ et present
-- nativement dans l'image postgres:18 (local et Testcontainers).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE annees_scolaires (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    libelle             VARCHAR(20)   NOT NULL,
    date_debut          DATE          NOT NULL,
    date_fin            DATE          NOT NULL,
    statut              VARCHAR(20)   NOT NULL DEFAULT 'PREPARATION',
    date_cloture        TIMESTAMPTZ,
    date_activation     TIMESTAMPTZ,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    CONSTRAINT ck_annees_scolaires_dates CHECK (date_fin > date_debut),
    CONSTRAINT ck_annees_scolaires_cloture CHECK (
        (statut <> 'CLOTUREE' AND date_cloture IS NULL) OR (statut = 'CLOTUREE' AND date_cloture IS NOT NULL)
    )
);

-- Libelle unique par etablissement parmi les annees actives (R4) : index
-- partiel, un libelle libere par desactivation redevient disponible
-- (CLAUDE.md, regle 4).
CREATE UNIQUE INDEX uk_annees_scolaires_libelle_actif ON annees_scolaires (etablissement_id, libelle) WHERE actif = TRUE;

-- Au plus une annee ACTIVE par etablissement (R8, A1) : contrainte de base,
-- pas seulement une regle de service (meme dispositif que
-- uk_sites_principal_actif, V6).
CREATE UNIQUE INDEX uk_annees_scolaires_active ON annees_scolaires (etablissement_id) WHERE statut = 'ACTIVE' AND actif = TRUE;

-- Non-chevauchement des plages de dates entre annees actives du meme
-- etablissement (A2, DELTA 2) : garde-fou de correction, retenu sans repli
-- applicatif seul.
ALTER TABLE annees_scolaires
    ADD CONSTRAINT ex_annees_scolaires_chevauchement
    EXCLUDE USING gist (etablissement_id WITH =, daterange(date_debut, date_fin, '[]') WITH &&)
    WHERE (actif = TRUE);

CREATE INDEX idx_annees_scolaires_etablissement ON annees_scolaires (etablissement_id);

CREATE TABLE annees_scolaires_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    libelle             VARCHAR(20),
    date_debut          DATE,
    date_fin            DATE,
    statut              VARCHAR(20),
    date_cloture        TIMESTAMPTZ,
    date_activation     TIMESTAMPTZ,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);
