-- US-07 (module admission) : journal en ajout seul des décisions prises sur
-- un dossier d'admission (accepter/refuser/mettre en liste d'attente).
-- Création pure, aucune suppression (ADR-0004).

CREATE TABLE decisions_admission (
    id                  UUID          PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    demande_id          UUID          NOT NULL REFERENCES demandes_admission (id),
    statut_precedent    VARCHAR(20)   NOT NULL
                          CHECK (statut_precedent IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'LISTE_ATTENTE', 'ANNULEE')),
    statut_nouveau      VARCHAR(20)   NOT NULL
                          CHECK (statut_nouveau IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'LISTE_ATTENTE', 'ANNULEE')),
    -- Note interne (US-07) : jamais transmise au parent, jamais incluse dans une notification.
    observation         VARCHAR(500),
    decide_par          UUID          NOT NULL,
    date_decision       TIMESTAMPTZ   NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ   NOT NULL,
    date_modification    TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255)
);

CREATE INDEX idx_decisions_admission_demande_date
    ON decisions_admission (demande_id, date_decision);

CREATE INDEX idx_decisions_admission_etablissement ON decisions_admission (etablissement_id);

CREATE TABLE decisions_admission_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    demande_id          UUID,
    statut_precedent    VARCHAR(20),
    statut_nouveau      VARCHAR(20),
    observation         VARCHAR(500),
    decide_par          UUID,
    date_decision       TIMESTAMPTZ,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ,
    date_modification    TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255),
    PRIMARY KEY (id, rev)
);
