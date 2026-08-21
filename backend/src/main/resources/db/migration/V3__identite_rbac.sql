-- Module identite (US-04) : socle sécurité et RBAC.
-- Email unique globalement (pas par établissement). Le rôle est porté par
-- l'affectation utilisateur/établissement (cumul possible), jamais par
-- l'utilisateur directement — voir docs/adr/0002-multi-etablissement.md.

CREATE TABLE utilisateurs (
    id                  UUID PRIMARY KEY,
    email               VARCHAR(255)  NOT NULL,
    mot_de_passe_hache  VARCHAR(255)  NOT NULL,
    nom_complet         VARCHAR(255)  NOT NULL,
    super_admin         BOOLEAN       NOT NULL DEFAULT FALSE,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

-- Index partiel : l'unicité ne porte que sur les comptes actifs, sinon un
-- email libéré par désactivation resterait bloqué à jamais (CLAUDE.md, règle 4).
CREATE UNIQUE INDEX uk_utilisateurs_email_actif ON utilisateurs (email) WHERE actif = TRUE;

-- Rôles et permissions sont des énumérations Java (RoleCode, Permission),
-- jamais des tables : le référentiel RBAC ne varie pas sans déploiement, et
-- une résolution des permissions par requête coûterait un aller-retour base
-- (ou un N+1) à chaque appel authentifié — précisément ce que la spec T-04
-- voulait éviter. Voir PermissionResolver (correction T-04, lot 1 n°2).

-- L'établissement n'existe pas encore en tant qu'entité (créée en T-05) :
-- etablissement_id est une simple colonne UUID, volontairement sans contrainte
-- de clé étrangère. Voir la justification applicative dans
-- AffectationEtablissementService (CLAUDE.md, arbitrage sur les références
-- sans FK).
CREATE TABLE affectations_etablissement (
    id                  UUID PRIMARY KEY,
    utilisateur_id      UUID NOT NULL REFERENCES utilisateurs (id),
    etablissement_id    UUID NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

CREATE UNIQUE INDEX uk_affectation_utilisateur_etablissement_actif
    ON affectations_etablissement (utilisateur_id, etablissement_id) WHERE actif = TRUE;

CREATE INDEX idx_affectation_etablissement ON affectations_etablissement (etablissement_id);

-- Cumul de rôles sur une même affectation : ensemble de codes RoleCode (pas
-- de table roles, voir plus haut), pas un rôle unique.
CREATE TABLE affectation_roles (
    affectation_id UUID          NOT NULL REFERENCES affectations_etablissement (id),
    role_code      VARCHAR(50)   NOT NULL,
    PRIMARY KEY (affectation_id, role_code)
);

-- Refresh token opaque, jamais un JWT, jamais stocké en clair (haché comme un
-- mot de passe). Pas de liste de révocation pour l'access token (stateless) :
-- seul le refresh token est persisté et peut être révoqué. famille_id relie
-- toute la chaîne de rotation d'un même login : présenter un jeton déjà
-- révoqué (rejeu, signal de vol) révoque immédiatement tous les jetons actifs
-- de la même famille (correction T-04, lot 1 n°4).
CREATE TABLE jetons_rafraichissement (
    id                  UUID PRIMARY KEY,
    utilisateur_id      UUID NOT NULL REFERENCES utilisateurs (id),
    jeton_hache         VARCHAR(255)  NOT NULL,
    famille_id          UUID          NOT NULL,
    -- Établissement actif au moment de l'émission (correction T-04, lot 2
    -- n°8) : NULL si l'utilisateur n'a alors aucune affectation active.
    -- Comme etablissement_id sur affectations_etablissement, aucune FK :
    -- l'entité Etablissement n'existe pas encore (T-05).
    etablissement_actif_id UUID,
    date_expiration     TIMESTAMPTZ   NOT NULL,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

CREATE UNIQUE INDEX uk_jeton_rafraichissement_hache ON jetons_rafraichissement (jeton_hache);
CREATE INDEX idx_jeton_rafraichissement_utilisateur ON jetons_rafraichissement (utilisateur_id);
CREATE INDEX idx_jeton_rafraichissement_famille ON jetons_rafraichissement (famille_id);
