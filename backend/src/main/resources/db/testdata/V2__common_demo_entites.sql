-- Table factice servant de support au contrôleur de démonstration T-03
-- (pagination, tri, filtres dynamiques). Ne porte aucune donnée métier et
-- n'est jamais exposée en dehors des profils local/test.
CREATE TABLE demo_entites (
    id                  UUID PRIMARY KEY,
    libelle             VARCHAR(255)  NOT NULL,
    categorie           VARCHAR(100),
    quantite            INTEGER,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);
