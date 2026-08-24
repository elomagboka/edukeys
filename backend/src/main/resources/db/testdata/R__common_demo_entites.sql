-- Table factice servant de support au contrôleur de démonstration T-03
-- (pagination, tri, filtres dynamiques). Ne porte aucune donnée métier et
-- n'est jamais exposée en dehors des profils local/test.
--
-- Convertie en migration répétable (T-05) : la création de la table doit
-- désormais être idempotente (IF NOT EXISTS), une migration répétable étant
-- rejouée à chaque changement de checksum du fichier.
--
-- Remise à zéro d'une base locale où V2__common_demo_entites.sql a déjà été
-- appliqué (Flyway signalera "detected applied migration not resolved
-- locally" puisque V2 a été renommé) : recréer la base locale
-- (docker compose down -v && docker compose up -d) est la voie la plus sûre
-- ici, ce fichier ne portant aucune donnée à préserver. `mvn flyway:repair`
-- suivi de `mvn flyway:migrate` fonctionne aussi (repair retire de
-- flyway_schema_history les entrées de migrations non résolues localement).
CREATE TABLE IF NOT EXISTS demo_entites (
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

-- Colonne ajoutée en T-05. DemoEntite est passée sous EntiteEtablissement en
-- sous-tâche 10 : c'est la seule façon de prouver que l'héritage du
-- @Filter Hibernate depuis une @MappedSuperclass fonctionne réellement. Pas
-- de FK vers etablissements : cette table de démonstration n'appartient à
-- aucun schéma applicatif, et l'ordre alphabétique d'exécution des
-- migrations répétables (celle-ci avant R__etablissement_demo.sql) rendrait
-- la contrainte impossible à satisfaire à la première exécution.
ALTER TABLE demo_entites ADD COLUMN IF NOT EXISTS etablissement_id UUID;

-- Toute nouvelle ligne doit désormais porter un établissement
-- (RemplisseurEtablissement le garantit côté JPA, sous-tâche 7) ; les lignes
-- historiques de ce jeu de démonstration en portent déjà un (INSERT
-- ci-dessous). Rendue NOT NULL uniquement ici, en db/testdata : jamais dans
-- db/migration pour une table de démonstration (CLAUDE.md).
ALTER TABLE demo_entites ALTER COLUMN etablissement_id SET NOT NULL;

-- Données de démonstration, réparties sur les deux établissements
-- (docs/adr/0002-multi-etablissement.md / T-08) : ids fixes, upsert par
-- ON CONFLICT ... DO UPDATE — ce fichier reste la source de vérité, une
-- correction ici se propage à une base déjà chargée.
--
-- date_creation n'est jamais dans le SET : elle appartient à la première
-- insertion. Seule date_modification est rafraîchie.
INSERT INTO demo_entites (id, libelle, categorie, quantite, actif, etablissement_id, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-8000-000000000401', 'Fournitures scolaires', 'stock', 50, TRUE, '01977000-0000-7000-9000-000000000001', now(), now()),
    ('01977000-0000-7000-8000-000000000402', 'Manuels de mathématiques', 'stock', 30, TRUE, '01977000-0000-7000-9000-000000000002', now(), now())
ON CONFLICT (id) DO UPDATE SET
    libelle           = EXCLUDED.libelle,
    categorie         = EXCLUDED.categorie,
    quantite          = EXCLUDED.quantite,
    actif             = EXCLUDED.actif,
    etablissement_id  = EXCLUDED.etablissement_id,
    date_modification = now();
