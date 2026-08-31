-- T-10 (US-00) : enrichissement d'Etablissement (identité, coordonnées,
-- référentiel), nouvelles entités Site et LogoEtablissement. Migration
-- rétrocompatible (docs/adr/0004-cicd-deploiement.md) : les colonnes sont
-- ajoutées NULL, remplies pour les lignes déjà en base, puis contraintes en
-- NOT NULL — jamais l'inverse.

-- 1) Colonnes ajoutées NULL d'abord.
ALTER TABLE etablissements
    ADD COLUMN sigle                  VARCHAR(20),
    ADD COLUMN type_etablissement     VARCHAR(20),
    ADD COLUMN ville                  VARCHAR(100),
    ADD COLUMN quartier               VARCHAR(150),
    ADD COLUMN boite_postale          VARCHAR(50),
    ADD COLUMN adresse_ligne          VARCHAR(255),
    ADD COLUMN email                  VARCHAR(320),
    ADD COLUMN telephone              VARCHAR(30),
    ADD COLUMN site_web               VARCHAR(255),
    ADD COLUMN pays_code              VARCHAR(2),
    ADD COLUMN fuseau_horaire         VARCHAR(64),
    ADD COLUMN devise_code            VARCHAR(3),
    ADD COLUMN langue_defaut          VARCHAR(10),
    ADD COLUMN referentiel_initialise BOOLEAN;

-- 2) Remplissage des lignes déjà en base (valeurs de repli neutres ; les
-- jeux de démonstration réels sont corrigés par db/testdata, jamais ici).
UPDATE etablissements
SET type_etablissement     = COALESCE(type_etablissement, 'COMPLEXE'),
    ville                  = COALESCE(ville, 'Lomé'),
    email                  = COALESCE(email, LOWER(code) || '@edukeys.local'),
    pays_code              = COALESCE(pays_code, 'TG'),
    fuseau_horaire         = COALESCE(fuseau_horaire, 'Africa/Lome'),
    devise_code            = COALESCE(devise_code, 'XOF'),
    langue_defaut          = COALESCE(langue_defaut, 'fr'),
    referentiel_initialise = COALESCE(referentiel_initialise, FALSE);

-- 3) Contraintes NOT NULL et valeurs par défaut pour les futures insertions.
ALTER TABLE etablissements
    ALTER COLUMN type_etablissement     SET NOT NULL,
    ALTER COLUMN ville                  SET NOT NULL,
    ALTER COLUMN email                  SET NOT NULL,
    ALTER COLUMN pays_code              SET NOT NULL,
    ALTER COLUMN pays_code              SET DEFAULT 'TG',
    ALTER COLUMN fuseau_horaire         SET NOT NULL,
    ALTER COLUMN fuseau_horaire         SET DEFAULT 'Africa/Lome',
    ALTER COLUMN devise_code            SET NOT NULL,
    ALTER COLUMN devise_code            SET DEFAULT 'XOF',
    ALTER COLUMN langue_defaut          SET NOT NULL,
    ALTER COLUMN langue_defaut          SET DEFAULT 'fr',
    ALTER COLUMN referentiel_initialise SET NOT NULL,
    ALTER COLUMN referentiel_initialise SET DEFAULT FALSE;

-- Un email libéré (établissement désactivé) ne doit pas rester bloqué à
-- jamais (CLAUDE.md, règle 4) : index partiel, comme pour le code (V4).
CREATE UNIQUE INDEX uk_etablissements_email_actif ON etablissements (LOWER(email)) WHERE actif = TRUE;

-- Sites (annexes) : pas de relation JPA vers Etablissement (Site n'importe
-- jamais le module etablissement en retour), la FK n'est posée qu'ici.
CREATE TABLE sites (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    code                VARCHAR(50)   NOT NULL,
    nom                 VARCHAR(255)  NOT NULL,
    principal           BOOLEAN       NOT NULL DEFAULT FALSE,
    ville               VARCHAR(100),
    quartier            VARCHAR(150),
    adresse_ligne       VARCHAR(255),
    telephone           VARCHAR(30),
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

CREATE UNIQUE INDEX uk_sites_code_actif ON sites (etablissement_id, code) WHERE actif = TRUE;
-- Garantit qu'un seul site principal actif existe par établissement (R4) :
-- pas seulement une règle applicative dans SiteService, une contrainte de base.
CREATE UNIQUE INDEX uk_sites_principal_actif ON sites (etablissement_id) WHERE principal = TRUE AND actif = TRUE;
CREATE INDEX idx_sites_etablissement ON sites (etablissement_id);

-- Logo d'établissement : le remplacement désactive l'ancien et en crée un
-- nouveau (R12), jamais une mise à jour en place, pour garder l'historique.
CREATE TABLE logos_etablissement (
    id                  UUID PRIMARY KEY,
    etablissement_id    UUID          NOT NULL REFERENCES etablissements (id),
    nom_fichier         VARCHAR(255)  NOT NULL,
    type_mime           VARCHAR(100)  NOT NULL,
    taille_octets       INTEGER       NOT NULL,
    empreinte_sha256    VARCHAR(64)      NOT NULL,
    contenu             BYTEA         NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

CREATE UNIQUE INDEX uk_logos_etablissement_actif ON logos_etablissement (etablissement_id) WHERE actif = TRUE;

-- Tables Envers (T-06) : etablissements_aud complétée avec les 14 nouvelles
-- colonnes (oubli classique sinon, Envers échoue au démarrage via
-- VerificateurAuditEnvers), sites_aud et logos_etablissement_aud créées.
ALTER TABLE etablissements_aud
    ADD COLUMN sigle                  VARCHAR(20),
    ADD COLUMN type_etablissement     VARCHAR(20),
    ADD COLUMN ville                  VARCHAR(100),
    ADD COLUMN quartier               VARCHAR(150),
    ADD COLUMN boite_postale          VARCHAR(50),
    ADD COLUMN adresse_ligne          VARCHAR(255),
    ADD COLUMN email                  VARCHAR(320),
    ADD COLUMN telephone              VARCHAR(30),
    ADD COLUMN site_web               VARCHAR(255),
    ADD COLUMN pays_code              VARCHAR(2),
    ADD COLUMN fuseau_horaire         VARCHAR(64),
    ADD COLUMN devise_code            VARCHAR(3),
    ADD COLUMN langue_defaut          VARCHAR(10),
    ADD COLUMN referentiel_initialise BOOLEAN;

CREATE TABLE sites_aud (
    id                  UUID    NOT NULL,
    rev                 BIGINT  NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    code                VARCHAR(50),
    nom                 VARCHAR(255),
    principal           BOOLEAN,
    ville               VARCHAR(100),
    quartier            VARCHAR(150),
    adresse_ligne       VARCHAR(255),
    telephone           VARCHAR(30),
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- contenu (BYTEA) est absent : @NotAudited (voir LogoEtablissement), une
-- révision de ce champ ne serait de toute façon jamais consultable.
CREATE TABLE logos_etablissement_aud (
    id                  UUID    NOT NULL,
    rev                 BIGINT  NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    etablissement_id    UUID,
    nom_fichier         VARCHAR(255),
    type_mime           VARCHAR(100),
    taille_octets       INTEGER,
    empreinte_sha256    VARCHAR(64),
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);
