-- Jeu de données de démonstration du module etablissement (T-05, enrichi en
-- T-10/US-00), chargé uniquement par les profils local/test. Migration
-- répétable et idempotente.
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
-- nombre_sites_actifs = 1 : chaque établissement de démo n'a que son site
-- principal (voir l'INSERT sur "sites" ci-dessous) — compteur dénormalisé
-- (durcissement post-revue T-10), à tenir cohérent avec les sites réels.
INSERT INTO etablissements (
    id, code, nom, sigle, type_etablissement, ville, quartier, email, telephone,
    pays_code, fuseau_horaire, devise_code, langue_defaut, referentiel_initialise,
    nombre_sites_actifs, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9000-000000000001', 'CSJ', 'Complexe Scolaire Jean Demo', 'CSJ', 'COMPLEXE',
     'Lomé', 'Bè', 'contact@csj-demo.tg', '+22890000001',
     'TG', 'Africa/Lome', 'XOF', 'fr', TRUE, 1, TRUE, now(), now()),
    ('01977000-0000-7000-9000-000000000002', 'ESN', 'École Sainte Nadège Demo', 'ESN', 'PRIMAIRE',
     'Kara', 'Centre', 'contact@esn-demo.tg', '+22890000002',
     'TG', 'Africa/Lome', 'XOF', 'fr', TRUE, 1, TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    code                    = EXCLUDED.code,
    nom                     = EXCLUDED.nom,
    sigle                   = EXCLUDED.sigle,
    type_etablissement      = EXCLUDED.type_etablissement,
    ville                   = EXCLUDED.ville,
    quartier                = EXCLUDED.quartier,
    email                   = EXCLUDED.email,
    telephone               = EXCLUDED.telephone,
    pays_code               = EXCLUDED.pays_code,
    fuseau_horaire          = EXCLUDED.fuseau_horaire,
    devise_code             = EXCLUDED.devise_code,
    langue_defaut           = EXCLUDED.langue_defaut,
    referentiel_initialise  = EXCLUDED.referentiel_initialise,
    nombre_sites_actifs     = EXCLUDED.nombre_sites_actifs,
    actif                   = EXCLUDED.actif,
    date_modification       = now();

-- Site principal de chaque établissement de démonstration (T-10/US-00 :
-- EtablissementService en crée un automatiquement à la création, ce jeu de
-- données rejoue le même invariant pour les établissements insérés
-- directement en SQL, hors du service applicatif).
INSERT INTO sites (id, etablissement_id, code, nom, principal, ville, quartier, actif, date_creation, date_modification)
VALUES
    ('01977000-0000-7000-9001-000000000001', '01977000-0000-7000-9000-000000000001',
     'CSJ-PRINCIPAL', 'Complexe Scolaire Jean Demo', TRUE, 'Lomé', 'Bè', TRUE, now(), now()),
    ('01977000-0000-7000-9001-000000000002', '01977000-0000-7000-9000-000000000002',
     'ESN-PRINCIPAL', 'École Sainte Nadège Demo', TRUE, 'Kara', 'Centre', TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
    code               = EXCLUDED.code,
    nom                = EXCLUDED.nom,
    principal          = EXCLUDED.principal,
    ville              = EXCLUDED.ville,
    quartier           = EXCLUDED.quartier,
    actif              = EXCLUDED.actif,
    date_modification  = now();
