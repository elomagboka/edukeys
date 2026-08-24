-- Jeu de données de démonstration du module identite (US-04), chargé
-- uniquement par les profils local/test (spring.flyway.locations). Migration
-- répétable et idempotente : ré-exécutable à chaque changement sans jamais
-- dupliquer les lignes (CLAUDE.md, section "Migrations et données de test").
--
-- Mot de passe en clair de tous les comptes de démonstration : Password123!

-- Rôles et permissions : énumérations Java (RoleCode, Permission), plus de
-- tables roles/permissions/role_permissions (correction T-04, lot 1 n°2).

-- Établissement de démonstration (l'entité Etablissement arrive en T-05 ;
-- ici, seul l'UUID est utilisé, comme le porte AffectationEtablissement).
-- 01977000-0000-7000-9000-000000000001

-- Comptes de démonstration (mot de passe : Password123!).
INSERT INTO utilisateurs (id, email, mot_de_passe_hache, nom_complet, super_admin, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-8000-000000000201', 'super.admin@edukeys.tg', '$2a$10$1LBQ2yIY6KnmX8yU5j87A.nQ66bZfAkoeWEUg3p52W8ssSOde9/QS', 'Super Administrateur Demo', TRUE,  TRUE, now(), now()),
    ('01977000-0000-7000-8000-000000000202', 'directeur@edukeys.tg',   '$2a$10$1LBQ2yIY6KnmX8yU5j87A.nQ66bZfAkoeWEUg3p52W8ssSOde9/QS', 'Directeur Demo',            FALSE, TRUE, now(), now()),
    -- Cas de référence de l'arbitrage T-04 n°1 : un enseignant dont l'enfant
    -- est scolarisé dans le même établissement porte aussi le rôle PARENT.
    ('01977000-0000-7000-8000-000000000203', 'enseignant.parent@edukeys.tg', '$2a$10$1LBQ2yIY6KnmX8yU5j87A.nQ66bZfAkoeWEUg3p52W8ssSOde9/QS', 'Enseignant Parent Demo', FALSE, TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    email              = EXCLUDED.email,
    mot_de_passe_hache = EXCLUDED.mot_de_passe_hache,
    nom_complet        = EXCLUDED.nom_complet,
    super_admin        = EXCLUDED.super_admin,
    actif              = EXCLUDED.actif,
    date_modification  = now();

INSERT INTO affectations_etablissement (id, utilisateur_id, etablissement_id, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-8000-000000000301', '01977000-0000-7000-8000-000000000202', '01977000-0000-7000-9000-000000000001', TRUE, now(), now()),
    ('01977000-0000-7000-8000-000000000302', '01977000-0000-7000-8000-000000000203', '01977000-0000-7000-9000-000000000001', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    utilisateur_id    = EXCLUDED.utilisateur_id,
    etablissement_id  = EXCLUDED.etablissement_id,
    actif             = EXCLUDED.actif,
    date_modification = now();

-- affectation_roles est une table de liaison pure : sa clé primaire couvre ses
-- deux seules colonnes, il n'y a donc rien à mettre à jour en cas de conflit —
-- DO UPDATE n'aurait aucun SET possible et DO NOTHING reste exact.
--
-- En revanche, l'upsert seul ne suffit pas à faire de ce fichier la source de
-- vérité : retirer un rôle de la liste ci-dessous laisserait la ligne
-- correspondante en base. Le DELETE ci-dessous rétablit la correspondance, en
-- restant strictement borné aux deux affectations de démonstration — il ne peut
-- pas toucher aux rôles d'une autre affectation.
DELETE FROM affectation_roles
WHERE affectation_id IN (
        '01977000-0000-7000-8000-000000000301',
        '01977000-0000-7000-8000-000000000302')
  AND (affectation_id, role_code) NOT IN (
        ('01977000-0000-7000-8000-000000000301', 'DIRECTION'),
        ('01977000-0000-7000-8000-000000000302', 'ENSEIGNANT'),
        ('01977000-0000-7000-8000-000000000302', 'PARENT'));

INSERT INTO affectation_roles (affectation_id, role_code) VALUES
    ('01977000-0000-7000-8000-000000000301', 'DIRECTION'),
    ('01977000-0000-7000-8000-000000000302', 'ENSEIGNANT'),
    ('01977000-0000-7000-8000-000000000302', 'PARENT')
ON CONFLICT DO NOTHING;
