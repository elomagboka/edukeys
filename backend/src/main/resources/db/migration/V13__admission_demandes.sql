-- US-06 (module admission) : pré-inscription en ligne. Création pure, aucune
-- suppression (ADR-0004). Statuts EN_ATTENTE/ACCEPTEE/REFUSEE/LISTE_ATTENTE
-- prévus dès maintenant (US-07), ANNULEE conservée pour la même raison.

CREATE TABLE demandes_admission (
    id                          UUID          PRIMARY KEY,
    etablissement_id            UUID          NOT NULL REFERENCES etablissements (id),
    reference                   VARCHAR(20)   NOT NULL,
    -- Code de suivi opaque (3e revue, point 1) : SecureRandom 128 bits, Crockford
    -- Base32 sans padding (26 caractères) -- seule valeur renvoyée à l'appelant
    -- public, jamais la référence séquentielle (voir GenerateurCodeSuiviAdmission).
    code_suivi                  VARCHAR(32)   NOT NULL,
    annee_scolaire_id           UUID          NOT NULL REFERENCES annees_scolaires (id),
    niveau_id                   UUID          NOT NULL REFERENCES niveaux (id),
    classe_id                   UUID          REFERENCES classes (id),
    -- Antichambre d'une inscription, localisée (CLAUDE.md, règle 9) : hors du
    -- filtre de sécurité (le site n'est pas un second niveau d'isolation),
    -- nullable -- renseignée plus tard (US-07/US-08), table vide à ce jour.
    site_id                     UUID          REFERENCES sites (id),
    nom                         VARCHAR(100)  NOT NULL,
    nom_normalise               VARCHAR(100)  NOT NULL,
    prenoms                     VARCHAR(150)  NOT NULL,
    prenoms_normalises          VARCHAR(150)  NOT NULL,
    date_naissance              DATE          NOT NULL,
    lieu_naissance              VARCHAR(100),
    sexe                        VARCHAR(1)    CHECK (sexe IN ('M', 'F')),
    nationalite                 VARCHAR(2),
    etablissement_origine       VARCHAR(150),
    responsable_nom             VARCHAR(100)  NOT NULL,
    responsable_prenoms         VARCHAR(150)  NOT NULL,
    responsable_lien            VARCHAR(10)   NOT NULL CHECK (responsable_lien IN ('PERE', 'MERE', 'TUTEUR', 'AUTRE')),
    responsable_telephone       VARCHAR(20)   NOT NULL,
    responsable_email           VARCHAR(254),
    statut                      VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE'
                                  CHECK (statut IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'LISTE_ATTENTE', 'ANNULEE')),
    canal                       VARCHAR(10)   NOT NULL CHECK (canal IN ('PUBLIC', 'ADMIN')),
    date_soumission             TIMESTAMPTZ   NOT NULL,
    date_decision                TIMESTAMPTZ,
    motif_decision               VARCHAR(500),
    decide_par                  UUID,
    consentement_donnees_at     TIMESTAMPTZ,
    ip_soumission_hash           VARCHAR(64),
    version                     BIGINT        NOT NULL DEFAULT 0,
    actif                       BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation          TIMESTAMPTZ,
    date_creation                TIMESTAMPTZ   NOT NULL,
    date_modification            TIMESTAMPTZ   NOT NULL,
    cree_par                    VARCHAR(255),
    modifie_par                  VARCHAR(255),
    CONSTRAINT ck_demandes_admission_consentement_public
        CHECK (canal <> 'PUBLIC' OR consentement_donnees_at IS NOT NULL)
);

CREATE UNIQUE INDEX uk_demandes_admission_reference
    ON demandes_admission (etablissement_id, reference) WHERE actif = TRUE;

-- 3e revue, point 1 : unicité du code de suivi opaque, index partiel comme le
-- reste des contraintes d'unicité (CLAUDE.md, règle 4).
CREATE UNIQUE INDEX uk_demandes_admission_code_suivi
    ON demandes_admission (code_suivi) WHERE actif = TRUE;

CREATE INDEX idx_demandes_admission_statut_date
    ON demandes_admission (etablissement_id, statut, date_soumission DESC) WHERE actif = TRUE;

CREATE INDEX idx_demandes_admission_etablissement ON demandes_admission (etablissement_id);

-- 3e revue, point 6 : index requis par les FK ci-dessus, dont l'US-07 aura besoin.
CREATE INDEX idx_demandes_admission_niveau ON demandes_admission (niveau_id);
CREATE INDEX idx_demandes_admission_classe ON demandes_admission (classe_id);
CREATE INDEX idx_demandes_admission_site ON demandes_admission (site_id);

-- Idempotence de la soumission (règle 3 de la spec US-06, robustesse I5) : un
-- même dossier (établissement, année, nom, prénoms, date de naissance) avec un
-- statut EN_ATTENTE ou LISTE_ATTENTE ne doit pas être dupliqué, y compris sous
-- soumission concurrente. Comparaison insensible à la casse et aux accents :
-- nom_normalise/prenoms_normalises sont calculés en Java (java.text.Normalizer)
-- à l'écriture, jamais par une fonction SQL native (CLAUDE.md, règle 2 — pas de
-- requête native sur une entité métier). Index UNIQUE partiel (pas seulement un
-- index de recherche) : une violation lors d'une course entre deux soumissions
-- simultanées est le signal que le service doit relire le dossier existant et
-- renvoyer son accusé plutôt que d'échouer.
CREATE UNIQUE INDEX uk_demandes_admission_doublon
    ON demandes_admission (etablissement_id, annee_scolaire_id, nom_normalise, prenoms_normalises, date_naissance)
    WHERE actif = TRUE AND statut IN ('EN_ATTENTE', 'LISTE_ATTENTE');

CREATE TABLE demandes_admission_aud (
    id                          UUID     NOT NULL,
    rev                         BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype                     SMALLINT NOT NULL,
    etablissement_id            UUID,
    reference                   VARCHAR(20),
    code_suivi                  VARCHAR(32),
    annee_scolaire_id           UUID,
    niveau_id                   UUID,
    classe_id                   UUID,
    site_id                     UUID,
    nom                         VARCHAR(100),
    nom_normalise               VARCHAR(100),
    prenoms                     VARCHAR(150),
    prenoms_normalises          VARCHAR(150),
    date_naissance              DATE,
    lieu_naissance              VARCHAR(100),
    sexe                        VARCHAR(1),
    nationalite                 VARCHAR(2),
    etablissement_origine       VARCHAR(150),
    responsable_nom             VARCHAR(100),
    responsable_prenoms         VARCHAR(150),
    responsable_lien            VARCHAR(10),
    responsable_telephone       VARCHAR(20),
    responsable_email           VARCHAR(254),
    statut                      VARCHAR(20),
    canal                       VARCHAR(10),
    date_soumission             TIMESTAMPTZ,
    date_decision                TIMESTAMPTZ,
    motif_decision               VARCHAR(500),
    decide_par                  UUID,
    consentement_donnees_at     TIMESTAMPTZ,
    ip_soumission_hash           VARCHAR(64),
    version                     BIGINT,
    actif                       BOOLEAN,
    date_desactivation          TIMESTAMPTZ,
    date_creation                TIMESTAMPTZ,
    date_modification            TIMESTAMPTZ,
    cree_par                    VARCHAR(255),
    modifie_par                  VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- Pièces jointes : métadonnées seules ici, le contenu binaire vit dans
-- contenus_pieces_jointes_admission (B3, revue). @Basic(fetch = LAZY) seul
-- était inopérant sans instrumentation de bytecode Hibernate — le contenu
-- était donc chargé via l'entité complète à chaque consultation du détail
-- d'un dossier. Séparer physiquement les deux tables garantit qu'aucune
-- requête sur pieces_jointes_admission ne peut jamais ramener le contenu.
CREATE TABLE pieces_jointes_admission (
    id                  UUID          PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    demande_id          UUID          NOT NULL REFERENCES demandes_admission (id),
    type_piece          VARCHAR(20)   NOT NULL CHECK (type_piece IN ('ACTE_NAISSANCE', 'BULLETIN', 'PHOTO', 'AUTRE')),
    nom_original        VARCHAR(255)  NOT NULL,
    type_mime           VARCHAR(50)   NOT NULL,
    taille_octets        BIGINT        NOT NULL,
    empreinte_sha256     VARCHAR(64)   NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ   NOT NULL,
    date_modification    TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255)
);

CREATE UNIQUE INDEX uk_pieces_jointes_admission_empreinte
    ON pieces_jointes_admission (demande_id, empreinte_sha256) WHERE actif = TRUE;

CREATE INDEX idx_pieces_jointes_admission_demande ON pieces_jointes_admission (demande_id);
CREATE INDEX idx_pieces_jointes_admission_etablissement ON pieces_jointes_admission (etablissement_id);

CREATE TABLE pieces_jointes_admission_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    demande_id          UUID,
    type_piece          VARCHAR(20),
    nom_original        VARCHAR(255),
    type_mime           VARCHAR(50),
    taille_octets        BIGINT,
    empreinte_sha256     VARCHAR(64),
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ,
    date_modification    TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- Contenu binaire, table séparée à clé partagée (B3, revue) : seule cette
-- table est lue au téléchargement d'une pièce, jamais au détail d'un dossier.
CREATE TABLE contenus_pieces_jointes_admission (
    id                  UUID          PRIMARY KEY REFERENCES pieces_jointes_admission (id),
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    empreinte_sha256     VARCHAR(64)   NOT NULL,
    contenu             BYTEA         NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ   NOT NULL,
    date_modification    TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255)
);

CREATE INDEX idx_contenus_pieces_jointes_admission_etablissement ON contenus_pieces_jointes_admission (etablissement_id);

-- _aud SANS la colonne contenu (CLAUDE.md, même motif que logos_etablissement_aud).
CREATE TABLE contenus_pieces_jointes_admission_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    empreinte_sha256     VARCHAR(64),
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ,
    date_modification    TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- Compteur de référence de dossier (PRE-<annee>-<sequence>), incrémenté en
-- JPQL avec verrou pessimiste (jamais de SQL natif sur une entité métier,
-- CLAUDE.md règle 2) : une ligne par (etablissement, annee).
CREATE TABLE compteurs_reference_admission (
    id                  UUID          PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    annee               INTEGER       NOT NULL,
    dernier             BIGINT        NOT NULL DEFAULT 0,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ   NOT NULL,
    date_modification    TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255)
);

CREATE UNIQUE INDEX uk_compteurs_reference_admission_annee
    ON compteurs_reference_admission (etablissement_id, annee) WHERE actif = TRUE;

CREATE TABLE compteurs_reference_admission_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    annee               INTEGER,
    dernier             BIGINT,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation        TIMESTAMPTZ,
    date_modification    TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par          VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- Bascule de la pré-inscription publique par établissement (ajout de
-- colonne, rétrocompatible - CLAUDE.md conventions).
ALTER TABLE etablissements
    ADD COLUMN admissions_ouvertes BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE etablissements_aud
    ADD COLUMN admissions_ouvertes BOOLEAN;
