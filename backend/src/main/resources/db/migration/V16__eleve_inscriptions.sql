-- US-08 (module eleve) : inscription d'un élève accepté, matricule, compte élève.
--
-- RÉTROCOMPATIBILITÉ (ADR-0004, ADR-0007) : Flyway tourne en preDeployCommand
-- pendant que l'ancienne instance sert encore. Tout est ajout (tables, colonnes
-- nullables, index) sauf UNE modification d'index, documentée en section 5.

-- ---------------------------------------------------------------------------
-- 1. Élèves. Identité seule, SANS site (ADR-0005) : le site organise
--    l'inscription, pas la personne.
-- ---------------------------------------------------------------------------
CREATE TABLE eleves (
    id                      UUID          PRIMARY KEY,
    etablissement_id        UUID          NOT NULL REFERENCES etablissements (id),
    matricule               VARCHAR(64)   NOT NULL,
    nom                     VARCHAR(100)  NOT NULL,
    nom_normalise           VARCHAR(100)  NOT NULL,
    prenoms                 VARCHAR(150)  NOT NULL,
    prenoms_normalises      VARCHAR(150)  NOT NULL,
    date_naissance          DATE          NOT NULL,
    lieu_naissance          VARCHAR(100),
    sexe                    VARCHAR(1)    CHECK (sexe IN ('M', 'F')),
    nationalite             VARCHAR(2),
    etablissement_origine   VARCHAR(150),
    utilisateur_id          UUID          NOT NULL REFERENCES utilisateurs (id),
    demande_admission_id    UUID          REFERENCES demandes_admission (id),
    actif                   BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation      TIMESTAMPTZ,
    date_creation           TIMESTAMPTZ   NOT NULL,
    date_modification       TIMESTAMPTZ   NOT NULL,
    cree_par                VARCHAR(255),
    modifie_par             VARCHAR(255)
);

-- EXCEPTION ASSUMÉE à la règle 4 de CLAUDE.md (index partiels WHERE actif) : le
-- matricule est un identifiant métier PÉRENNE (bulletins, reçus). Un élève
-- radié garde son matricule, qui ne doit jamais être réattribué : unicité
-- ABSOLUE, sans WHERE actif.
CREATE UNIQUE INDEX uk_eleves_matricule ON eleves (etablissement_id, matricule);

-- Même exception : un dossier d'admission donne naissance à AU PLUS UN élève,
-- même désactivé (Q-B, validée par le PO). Filet de dernier recours de la
-- double inscription (le verrou du dossier est la première ligne de défense).
CREATE UNIQUE INDEX uk_eleves_demande_admission ON eleves (demande_admission_id)
    WHERE demande_admission_id IS NOT NULL;

-- Un compte ne désigne qu'un élève actif ; un compte libéré (désactivé) peut être repris.
CREATE UNIQUE INDEX uk_eleves_utilisateur_actif ON eleves (utilisateur_id) WHERE actif = TRUE;

-- Détection d'homonymes (une requête, lecture par index).
CREATE INDEX idx_eleves_homonymie
    ON eleves (etablissement_id, nom_normalise, prenoms_normalises, date_naissance) WHERE actif = TRUE;
CREATE INDEX idx_eleves_etablissement ON eleves (etablissement_id);

CREATE TABLE eleves_aud (
    id                      UUID     NOT NULL,
    rev                     BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype                 SMALLINT NOT NULL,
    etablissement_id        UUID,
    matricule               VARCHAR(64),
    nom                     VARCHAR(100),
    nom_normalise           VARCHAR(100),
    prenoms                 VARCHAR(150),
    prenoms_normalises      VARCHAR(150),
    date_naissance          DATE,
    lieu_naissance          VARCHAR(100),
    sexe                    VARCHAR(1),
    nationalite             VARCHAR(2),
    etablissement_origine   VARCHAR(150),
    utilisateur_id          UUID,
    demande_admission_id    UUID,
    actif                   BOOLEAN,
    date_desactivation      TIMESTAMPTZ,
    date_creation           TIMESTAMPTZ,
    date_modification       TIMESTAMPTZ,
    cree_par                VARCHAR(255),
    modifie_par             VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- ---------------------------------------------------------------------------
-- 2. Inscriptions (un élève, une année, une classe).
-- ---------------------------------------------------------------------------
CREATE TABLE inscriptions (
    id                  UUID          PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    eleve_id            UUID          NOT NULL REFERENCES eleves (id),
    annee_scolaire_id   UUID          NOT NULL REFERENCES annees_scolaires (id),
    classe_id           UUID          NOT NULL REFERENCES classes (id),
    -- Recopié de la classe à l'inscription (CLAUDE.md, règle 9) ; peut diverger
    -- si le site de la classe change ensuite (US-11).
    site_id             UUID          NOT NULL REFERENCES sites (id),
    date_inscription    TIMESTAMPTZ   NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

CREATE UNIQUE INDEX uk_inscriptions_eleve_annee ON inscriptions (eleve_id, annee_scolaire_id) WHERE actif = TRUE;
CREATE INDEX idx_inscriptions_classe_actif ON inscriptions (classe_id) WHERE actif = TRUE;
CREATE INDEX idx_inscriptions_eleve ON inscriptions (eleve_id);
CREATE INDEX idx_inscriptions_annee ON inscriptions (annee_scolaire_id);
CREATE INDEX idx_inscriptions_site ON inscriptions (site_id);
CREATE INDEX idx_inscriptions_etablissement ON inscriptions (etablissement_id);

CREATE TABLE inscriptions_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    eleve_id            UUID,
    annee_scolaire_id   UUID,
    classe_id           UUID,
    site_id             UUID,
    date_inscription    TIMESTAMPTZ,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- ---------------------------------------------------------------------------
-- 3. Compteur de matricule : une ligne par (établissement, année de début de
--    l'année scolaire). Jamais désactivé : la ligne porte l'historique de la
--    séquence, la désactiver ferait repartir la numérotation à zéro.
-- ---------------------------------------------------------------------------
CREATE TABLE compteurs_matricule (
    id                  UUID          PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    annee               INTEGER       NOT NULL,
    dernier             BIGINT        NOT NULL DEFAULT 0 CHECK (dernier BETWEEN 0 AND 99999),
    actif               BOOLEAN       NOT NULL DEFAULT TRUE CHECK (actif = TRUE),
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

-- Unicité ABSOLUE (exception assumée à la règle 4, voir uk_eleves_matricule).
CREATE UNIQUE INDEX uk_compteurs_matricule_annee ON compteurs_matricule (etablissement_id, annee);

CREATE TABLE compteurs_matricule_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    annee               INTEGER,
    dernier             BIGINT,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- ---------------------------------------------------------------------------
-- 4. Lien dossier -> élève (colonnes nullables : rétrocompatible).
-- ---------------------------------------------------------------------------
ALTER TABLE demandes_admission
    ADD COLUMN eleve_id          UUID REFERENCES eleves (id),
    ADD COLUMN date_inscription  TIMESTAMPTZ;

ALTER TABLE demandes_admission
    ADD CONSTRAINT ck_demandes_admission_eleve_acceptee
        CHECK (eleve_id IS NULL OR statut = 'ACCEPTEE'),
    ADD CONSTRAINT ck_demandes_admission_eleve_date
        CHECK ((eleve_id IS NULL) = (date_inscription IS NULL));

-- Même exception que uk_eleves_demande_admission (lien pérenne, sans WHERE actif).
CREATE UNIQUE INDEX uk_demandes_admission_eleve ON demandes_admission (eleve_id) WHERE eleve_id IS NOT NULL;

ALTER TABLE demandes_admission_aud
    ADD COLUMN eleve_id          UUID,
    ADD COLUMN date_inscription  TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- 5. Q5 : un dossier ACCEPTEE reste un dossier « vivant » pour l'enfant, sinon
--    une re-soumission publique créerait un second dossier d'un enfant déjà
--    accepté. L'index de doublon (V13) couvre donc aussi ACCEPTEE.
--
--    FENÊTRE DE DÉPLOIEMENT (risque accepté) : pendant la bascule, l'ancienne
--    instance ne relit pas les dossiers ACCEPTEE après un conflit d'unicité
--    (elle ne les connaît pas comme doublon) : une re-soumission d'un enfant
--    ACCEPTEE traitée par l'ancien code peut y répondre 500 au lieu de 201.
--    Aucune donnée n'est corrompue, la requête est rejouable.
--
--    Détection préalable : si des dossiers existants empêchent la création du
--    nouvel index, la migration échoue avec un message actionnable plutôt
--    qu'une violation de contrainte brute.
-- ---------------------------------------------------------------------------
-- DETECTION_DOUBLONS_DEBUT
DO $$
DECLARE
    nb_groupes  INTEGER;
    references_ TEXT;
BEGIN
    SELECT count(*), string_agg(g.refs, ' | ' ORDER BY g.refs)
      INTO nb_groupes, references_
      FROM (
            SELECT string_agg(d.reference, ', ' ORDER BY d.reference) AS refs
              FROM demandes_admission d
             WHERE d.actif = TRUE
               AND d.statut IN ('EN_ATTENTE', 'LISTE_ATTENTE', 'ACCEPTEE')
             GROUP BY d.etablissement_id, d.annee_scolaire_id, d.nom_normalise, d.prenoms_normalises, d.date_naissance
            HAVING count(*) > 1
           ) g;

    IF nb_groupes > 0 THEN
        RAISE EXCEPTION
            'Migration V16 impossible : % groupe(s) de dossiers d''admission en doublon (meme etablissement, annee, nom, prenoms et date de naissance parmi EN_ATTENTE, LISTE_ATTENTE, ACCEPTEE). References concernees : %. Marche a suivre : desactiver (actif = false) ou annuler tous les dossiers de chaque groupe sauf un, puis relancer la migration.',
            nb_groupes, references_;
    END IF;
END
$$;
-- DETECTION_DOUBLONS_FIN

DROP INDEX uk_demandes_admission_doublon;

CREATE UNIQUE INDEX uk_demandes_admission_doublon
    ON demandes_admission (etablissement_id, annee_scolaire_id, nom_normalise, prenoms_normalises, date_naissance)
    WHERE actif = TRUE AND statut IN ('EN_ATTENTE', 'LISTE_ATTENTE', 'ACCEPTEE');
