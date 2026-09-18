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
    ('01977000-0000-7000-9004-000000000001', '01977000-0000-7000-9000-000000000001', 'Scientifique', 'D', '01977000-0000-7000-9002-000000000002', TRUE, now(), now()),
    ('01977000-0000-7000-9004-000000000002', '01977000-0000-7000-9000-000000000001', 'Littéraire', 'A', '01977000-0000-7000-9002-000000000002', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    libelle            = EXCLUDED.libelle,
    code               = EXCLUDED.code,
    cycle_id           = EXCLUDED.cycle_id,
    date_modification  = now();

-- Matières (US-03), rattachées via affectations_matieres au couple
-- niveau/filière. Mathématiques illustre un coefficient différent selon la
-- filière ; Philosophie n'est affectée qu'en Terminale A ; Français est
-- affecté au collège sans filière (filiere_id NULL = toutes filières).
INSERT INTO matieres (id, etablissement_id, libelle, code, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9005-000000000001', '01977000-0000-7000-9000-000000000001', 'Mathématiques', 'MATHS', TRUE, now(), now()),
    ('01977000-0000-7000-9005-000000000002', '01977000-0000-7000-9000-000000000001', 'Philosophie', 'PHILO', TRUE, now(), now()),
    ('01977000-0000-7000-9005-000000000003', '01977000-0000-7000-9000-000000000001', 'Français', 'FR', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    libelle            = EXCLUDED.libelle,
    code               = EXCLUDED.code,
    date_modification  = now();

INSERT INTO affectations_matieres (id, etablissement_id, matiere_id, niveau_id, filiere_id, coefficient, volume_horaire, obligatoire, actif, date_creation, date_modification)
VALUES
    -- Mathématiques : coef 4 en Terminale D, coef 2 en Terminale A.
    ('01977000-0000-7000-9006-000000000001', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9005-000000000001', '01977000-0000-7000-9003-000000000003', '01977000-0000-7000-9004-000000000001', 4.00, 6.0, TRUE, TRUE, now(), now()),
    ('01977000-0000-7000-9006-000000000002', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9005-000000000001', '01977000-0000-7000-9003-000000000003', '01977000-0000-7000-9004-000000000002', 2.00, 3.0, TRUE, TRUE, now(), now()),
    -- Philosophie : uniquement en Terminale A.
    ('01977000-0000-7000-9006-000000000003', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9005-000000000002', '01977000-0000-7000-9003-000000000003', '01977000-0000-7000-9004-000000000002', 4.00, 4.0, TRUE, TRUE, now(), now()),
    -- Français : collège (6ème/5ème), toutes filières confondues (filiere_id NULL).
    ('01977000-0000-7000-9006-000000000004', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9005-000000000003', '01977000-0000-7000-9003-000000000001', NULL, 4.00, 5.0, TRUE, TRUE, now(), now()),
    ('01977000-0000-7000-9006-000000000005', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9005-000000000003', '01977000-0000-7000-9003-000000000002', NULL, 4.00, 5.0, TRUE, TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    coefficient        = EXCLUDED.coefficient,
    volume_horaire     = EXCLUDED.volume_horaire,
    obligatoire        = EXCLUDED.obligatoire,
    date_modification  = now();

-- Année scolaire de démonstration (US-01), ACTIVE, et ses trois trimestres
-- (US-05). Dates calées sur CURRENT_DATE plutôt que figées : la migration
-- répétable recalcule à chaque exécution une année qui englobe aujourd'hui,
-- avec un trimestre "en cours" (T2) — utile pour démontrer
-- GET /api/v1/periodes-academiques/en-cours sans dépendre de la date de
-- chargement du jeu de données. Un écart de 10 jours sépare T1 et T2 (trous
-- autorisés entre périodes, US-05).
INSERT INTO annees_scolaires (id, etablissement_id, libelle, date_debut, date_fin, statut, date_activation, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9007-000000000001', '01977000-0000-7000-9000-000000000001', 'Année démo',
     CURRENT_DATE - INTERVAL '60 days', CURRENT_DATE + INTERVAL '280 days', 'ACTIVE', now(), TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    date_debut         = EXCLUDED.date_debut,
    date_fin           = EXCLUDED.date_fin,
    statut             = EXCLUDED.statut,
    date_modification  = now();

INSERT INTO periodes_academiques (id, etablissement_id, annee_scolaire_id, libelle, type, ordre, date_debut, date_fin, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9008-000000000001', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9007-000000000001',
     '1er trimestre', 'TRIMESTRE', 1, CURRENT_DATE - INTERVAL '60 days', CURRENT_DATE - INTERVAL '1 days', TRUE, now(), now()),
    ('01977000-0000-7000-9008-000000000002', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9007-000000000001',
     '2e trimestre', 'TRIMESTRE', 2, CURRENT_DATE, CURRENT_DATE + INTERVAL '69 days', TRUE, now(), now()),
    ('01977000-0000-7000-9008-000000000003', '01977000-0000-7000-9000-000000000001', '01977000-0000-7000-9007-000000000001',
     '3e trimestre', 'TRIMESTRE', 3, CURRENT_DATE + INTERVAL '80 days', CURRENT_DATE + INTERVAL '260 days', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    date_debut         = EXCLUDED.date_debut,
    date_fin           = EXCLUDED.date_fin,
    date_modification  = now();
