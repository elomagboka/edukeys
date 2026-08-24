-- T-05 : socle multi-établissement. Etablissement est une entité minimale
-- (id, code, nom, actif) ; elle sera enrichie en T-10 (US-00). Migration
-- rétrocompatible : aucun DROP, uniquement des ajouts et des FK rétroactives
-- sur des colonnes déjà existantes (docs/adr/0004-cicd-deploiement.md).

CREATE TABLE etablissements (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(50)   NOT NULL,
    nom                 VARCHAR(255)  NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

-- Index partiel : seul un code d'établissement actif doit être unique ; un
-- code libéré par désactivation ne doit pas rester bloqué à jamais
-- (CLAUDE.md, règle 4).
CREATE UNIQUE INDEX uk_etablissements_code_actif ON etablissements (code) WHERE actif = TRUE;

-- FK rétroactives : les colonnes existaient déjà en V3, posées sans
-- contrainte faute d'entité cible (voir commentaires historiques dans
-- AffectationEtablissement et JetonRafraichissement). L'entité Etablissement
-- existant désormais, la contrainte peut être ajoutée sans migration
-- destructive.
ALTER TABLE affectations_etablissement
    ADD CONSTRAINT fk_affectation_etablissement
    FOREIGN KEY (etablissement_id) REFERENCES etablissements (id);

ALTER TABLE jetons_rafraichissement
    ADD CONSTRAINT fk_jeton_etablissement_actif
    FOREIGN KEY (etablissement_actif_id) REFERENCES etablissements (id);
