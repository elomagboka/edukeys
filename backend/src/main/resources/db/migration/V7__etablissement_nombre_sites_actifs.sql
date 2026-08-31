-- T-10 (durcissement post-revue) : nombreSites, lu par la liste paginée des
-- établissements, était calculé par une requête agrégée soumise au filtre
-- Hibernate multi-établissement — renvoyant 0 pour un appelant SUPER_ADMIN
-- sans contexte ouvert (valeur fausse). Remplacé par un compteur dénormalisé,
-- maintenu par Etablissement#incrementerSitesActifs/decrementerSitesActifs
-- aux seuls points qui font varier le compte (SiteService.creer/desactiver,
-- création du site principal dans EtablissementService.creer).

-- 1) Colonne ajoutée avec une valeur par défaut, migration rétrocompatible
-- (docs/adr/0004-cicd-deploiement.md).
ALTER TABLE etablissements
    ADD COLUMN nombre_sites_actifs INTEGER NOT NULL DEFAULT 0;

-- 2) Rétro-calcul pour les lignes déjà en base, à partir des sites réels.
UPDATE etablissements e
SET nombre_sites_actifs = (
    SELECT COUNT(*) FROM sites s WHERE s.etablissement_id = e.id AND s.actif = TRUE
);

-- 3) Table Envers (T-06) : oubli classique sinon, VerificateurAuditEnvers
-- fait échouer le démarrage.
ALTER TABLE etablissements_aud
    ADD COLUMN nombre_sites_actifs INTEGER;
