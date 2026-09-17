-- Jeu de données de démonstration du référentiel académique (US-02), chargé
-- uniquement par les profils local/test. Migration répétable et idempotente.
-- Limité à cycles/niveaux/filières (référentiel pérenne) : aucune classe de
-- démo ici, faute d'année scolaire de démonstration déjà chargée par le
-- module academique (US-01 n'en fournit pas encore).

INSERT INTO cycles (id, etablissement_id, libelle, code, rang, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9002-000000000001', '01977000-0000-7000-9000-000000000001', 'Collège', 'COLLEGE', 1, TRUE, now(), now()),
    ('01977000-0000-7000-9002-000000000002', '01977000-0000-7000-9000-000000000001', 'Lycée', 'LYCEE', 2, TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    libelle            = EXCLUDED.libelle,
    code               = EXCLUDED.code,
    rang               = EXCLUDED.rang,
    date_modification  = now();

INSERT INTO niveaux (id, etablissement_id, libelle, code, rang, cycle_id, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9003-000000000001', '01977000-0000-7000-9000-000000000001', '6ème', '6E', 1, '01977000-0000-7000-9002-000000000001', TRUE, now(), now()),
    ('01977000-0000-7000-9003-000000000002', '01977000-0000-7000-9000-000000000001', '5ème', '5E', 2, '01977000-0000-7000-9002-000000000001', TRUE, now(), now()),
    ('01977000-0000-7000-9003-000000000003', '01977000-0000-7000-9000-000000000001', 'Terminale', 'TLE', 3, '01977000-0000-7000-9002-000000000002', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    libelle            = EXCLUDED.libelle,
    code               = EXCLUDED.code,
    rang               = EXCLUDED.rang,
    cycle_id           = EXCLUDED.cycle_id,
    date_modification  = now();

INSERT INTO filieres (id, etablissement_id, libelle, code, cycle_id, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9004-000000000001', '01977000-0000-7000-9000-000000000001', 'Scientifique', 'D', '01977000-0000-7000-9002-000000000002', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    libelle            = EXCLUDED.libelle,
    code               = EXCLUDED.code,
    cycle_id           = EXCLUDED.cycle_id,
    date_modification  = now();
