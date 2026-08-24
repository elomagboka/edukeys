-- Jeu de données de démonstration du module etablissement (T-05), chargé
-- uniquement par les profils local/test. Migration répétable et idempotente.
--
-- Deux établissements distincts : exigence ADR-0002 / T-08 — c'est la seule
-- façon de rendre détectable une fuite d'isolation entre établissements.
-- Doit s'exécuter avant R__identite_comptes_demo.sql (ordre alphabétique des
-- migrations répétables : "etablissement_demo" < "identite_comptes_demo"),
-- car les affectations de ce dernier référencent l'établissement 000000001
-- par une contrainte de clé étrangère posée en V4.

-- ON CONFLICT ... DO UPDATE plutôt que DO NOTHING : ce fichier est la source
-- de vérité des données de démonstration. Avec DO NOTHING, corriger un nom ici
-- n'aurait aucun effet sur une base déjà chargée, et le contenu réel divergerait
-- silencieusement du fichier.
--
-- date_creation est volontairement absente du SET : elle appartient à la
-- première insertion et ne doit jamais être réécrite. Seule date_modification
-- est rafraîchie, exactement comme le ferait JPA Auditing sur une mise à jour.
INSERT INTO etablissements (id, code, nom, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9000-000000000001', 'CSJ', 'Complexe Scolaire Jean Demo', TRUE, now(), now()),
    ('01977000-0000-7000-9000-000000000002', 'ESN', 'École Sainte Nadège Demo',    TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    code              = EXCLUDED.code,
    nom               = EXCLUDED.nom,
    actif             = EXCLUDED.actif,
    date_modification = now();
