-- T-06 : historisation Hibernate Envers. @Audited est posé sur BaseEntity,
-- donc sur toute entité métier qui en hérite (docs/SPRINT-0.md, T-06). Chaque
-- table _aud reprend les colonnes de la table d'origine, toutes NULLABLE
-- (une révision de suppression ne porte que la clé et les colonnes
-- techniques rev/revtype, jamais l'état métier) plus (id, rev) en clé
-- primaire composite. La table demo_entites n'a pas d'équivalent ici : elle
-- n'existe qu'en db/testdata (local/test), donc demo_entites_aud y vit aussi
-- (CLAUDE.md : jamais de structure de démonstration dans db/migration).

-- Table de révision : remplace REVINFO par défaut pour porter l'auteur
-- (RevisionAuteur, common.audit) résolu depuis le contexte de sécurité, avec
-- le même repli "system" que cree_par/modifie_par (JpaAuditingConfig).
CREATE SEQUENCE seq_revisions START WITH 1 INCREMENT BY 1;

CREATE TABLE revisions (
    rev      BIGINT PRIMARY KEY,
    revtstmp BIGINT       NOT NULL,
    auteur   VARCHAR(255)
);

CREATE TABLE etablissements_aud (
    id                  UUID    NOT NULL,
    rev                 BIGINT  NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    code                VARCHAR(50),
    nom                 VARCHAR(255),
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE utilisateurs_aud (
    id                  UUID    NOT NULL,
    rev                 BIGINT  NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    email               VARCHAR(255),
    mot_de_passe_hache  VARCHAR(255),
    nom_complet         VARCHAR(255),
    super_admin         BOOLEAN,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

CREATE TABLE affectations_etablissement_aud (
    id                  UUID    NOT NULL,
    rev                 BIGINT  NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    utilisateur_id      UUID,
    etablissement_id    UUID,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);

-- Collection @ElementCollection roles : Envers audite la table de collection
-- elle-même, une ligne par (révision, rôle ajouté ou retiré).
CREATE TABLE affectation_roles_aud (
    affectation_id  UUID        NOT NULL,
    rev             BIGINT      NOT NULL REFERENCES revisions (rev),
    revtype         SMALLINT    NOT NULL,
    role_code       VARCHAR(50) NOT NULL,
    PRIMARY KEY (affectation_id, rev, role_code)
);

CREATE TABLE jetons_rafraichissement_aud (
    id                      UUID    NOT NULL,
    rev                     BIGINT  NOT NULL REFERENCES revisions (rev),
    revtype                 SMALLINT NOT NULL,
    utilisateur_id          UUID,
    jeton_hache             VARCHAR(255),
    famille_id              UUID,
    etablissement_actif_id  UUID,
    date_expiration         TIMESTAMPTZ,
    actif                   BOOLEAN,
    date_desactivation      TIMESTAMPTZ,
    date_creation           TIMESTAMPTZ,
    date_modification       TIMESTAMPTZ,
    cree_par                VARCHAR(255),
    modifie_par             VARCHAR(255),
    PRIMARY KEY (id, rev)
);
