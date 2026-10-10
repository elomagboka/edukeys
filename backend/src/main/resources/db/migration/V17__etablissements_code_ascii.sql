-- US-08 : le code d'établissement entre dans le matricule (CODE-AAAA-NNNNN,
-- définitif : bulletins, reçus) et dans l'identifiant de connexion des élèves.
-- Il est donc restreint à l'ASCII sûr : 2 à 10 majuscules ou chiffres. Pas de
-- tiret : c'est le séparateur du matricule. Pas d'accent ni d'espace : un
-- matricule se recopie à la main et se saisit sur un clavier de téléphone.
--
-- Contrôle appliqué à TOUTES les lignes, actives ou non (un établissement
-- réactivé retrouve son code). Si des codes existants sont hors format, la
-- migration échoue en les nommant, plutôt que par une violation de contrainte
-- brute : les corriger (UPDATE etablissements SET code = ...) puis relancer.
--
-- RÉTROCOMPATIBILITÉ : pendant la fenêtre de déploiement, l'ancienne instance
-- n'applique pas ce format côté Java ; une création d'établissement hors format
-- y échouerait sur la contrainte (erreur franche, aucune donnée corrompue).
DO $$
DECLARE
    fautifs TEXT;
BEGIN
    SELECT string_agg('"' || code || '"', ', ' ORDER BY code)
      INTO fautifs
      FROM etablissements
     WHERE code !~ '^[A-Z0-9]{2,10}$';

    IF fautifs IS NOT NULL THEN
        RAISE EXCEPTION
            'Migration V17 impossible : codes d''etablissement hors format [A-Z0-9]{2,10} : %. Corriger ces codes (majuscules et chiffres uniquement, 2 a 10 caracteres, sans tiret ni espace ni accent) avant de relancer la migration.',
            fautifs;
    END IF;
END
$$;

ALTER TABLE etablissements
    ADD CONSTRAINT ck_etablissements_code_format CHECK (code ~ '^[A-Z0-9]{2,10}$');
