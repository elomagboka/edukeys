-- US-08a : identifiant de connexion distinct de l'email.
-- US-08 créera des comptes élèves dont l'identifiant est le matricule, sans
-- email. L'email devient nullable ; l'identifiant de connexion porte
-- désormais l'unicité de la connexion.
--
-- Convention de casse : identique à l'email (V3) — index unique simple, la
-- normalisation (trim + minuscules, Locale.ROOT) étant faite en Java avant
-- toute écriture ou recherche (common.securite.IdentifiantConnexion). Côté
-- SQL, btrim() ne retire que les espaces alors que String.trim() retire tout
-- caractère <= U+0020 : la différence ne porte que sur des caractères de
-- contrôle, absents d'un email validé (@Email).
-- Même réserve pour lower() : celui de PostgreSQL dépend de la locale de la
-- base (LC_CTYPE) alors que toLowerCase(Locale.ROOT) applique Unicode. Pour
-- l'ASCII (emails, matricules) les deux coïncident ; un email contenant des
-- lettres non ASCII pourrait donc être normalisé différemment par le rattrapage
-- ci-dessous (ou le trigger) et par Java — au pire, ce compte ne se connecte
-- qu'avec l'identifiant tel que Java le normalise, corrigeable par UPDATE.
--
-- RÉTROCOMPATIBILITÉ (ADR-0004, ADR-0007) : Flyway tourne en preDeployCommand
-- pendant que l'ancienne instance sert encore. L'ancien code ignore la colonne
-- identifiant_connexion et ne l'inclut dans aucun INSERT : elle reste donc
-- NULLABLE dans cette version, et un trigger la remplit depuis l'email pour les
-- comptes créés par l'ancien code (sinon un compte créé pendant la fenêtre de
-- déploiement ne pourrait pas se connecter avec le nouveau code).
--
-- SUIVI (version suivante, volontairement PAS faite ici) : migration de
-- durcissement = UPDATE filet (identifiant_connexion = lower(btrim(email))
-- WHERE identifiant_connexion IS NULL), ALTER COLUMN identifiant_connexion SET
-- NOT NULL, DROP TRIGGER trg_utilisateurs_identifiant_connexion puis DROP
-- FUNCTION utilisateurs_poser_identifiant_connexion(). Suivi : issue #106 ;
-- elle ne doit partir qu'une fois l'ancien code retiré.
--
-- Envers : l'UPDATE de rattrapage ci-dessous ne crée AUCUNE révision d'audit
-- (SQL direct, hors Hibernate) ; les lignes de utilisateurs_aud antérieures à
-- cette migration ont donc identifiant_connexion NULL, ce qui est attendu.

ALTER TABLE utilisateurs ADD COLUMN identifiant_connexion VARCHAR(255);

-- Trigger posé AVANT le rattrapage et avant l'index : tout compte créé par
-- l'ancien code, y compris pendant l'exécution de cette migration, reçoit son
-- identifiant. Ne remplace jamais une valeur fournie (nouveau code).
CREATE FUNCTION utilisateurs_poser_identifiant_connexion() RETURNS trigger AS $$
BEGIN
    IF NEW.identifiant_connexion IS NULL AND NEW.email IS NOT NULL THEN
        NEW.identifiant_connexion := lower(btrim(NEW.email));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_utilisateurs_identifiant_connexion
    BEFORE INSERT OR UPDATE ON utilisateurs
    FOR EACH ROW EXECUTE FUNCTION utilisateurs_poser_identifiant_connexion();

UPDATE utilisateurs SET identifiant_connexion = lower(btrim(email))
WHERE identifiant_connexion IS NULL AND email IS NOT NULL;

-- Index partiel (CLAUDE.md, règle 4) : un identifiant libéré par
-- désactivation redevient utilisable.
CREATE UNIQUE INDEX uk_utilisateurs_identifiant_connexion_actif
    ON utilisateurs (identifiant_connexion) WHERE actif = TRUE;

-- Un compte sans email est désormais légitime (élève). L'index partiel
-- uk_utilisateurs_email_actif (V3) est conservé : les NULL n'entrent jamais
-- en collision dans un index unique PostgreSQL. Rétrocompatible : l'ancien
-- code écrit toujours un email.
ALTER TABLE utilisateurs ALTER COLUMN email DROP NOT NULL;

ALTER TABLE utilisateurs_aud ADD COLUMN identifiant_connexion VARCHAR(255);
