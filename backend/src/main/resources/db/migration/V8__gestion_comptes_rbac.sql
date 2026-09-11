-- US-04 (issue #24) : gestion des utilisateurs et rôles (RBAC), au-delà du
-- socle T-04/T-05.

-- 1) Mot de passe temporaire imposé au premier accès (pas de canal email
-- avant le Sprint 10, voir docs/adr/0006-notifications.md) : tant que ce
-- drapeau est vrai, seul un changement de mot de passe est autorisé
-- (garde centrale dans identite.security). Valeur par défaut FALSE pour les
-- comptes déjà existants (créés hors de ce flux, ex. données de démo).
ALTER TABLE utilisateurs
    ADD COLUMN mot_de_passe_a_changer BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE utilisateurs_aud
    ADD COLUMN mot_de_passe_a_changer BOOLEAN;

-- 2) Organisation interne (ADR-0005) : le site n'est jamais un second niveau
-- de cloisonnement de sécurité, donc nullable et absent de tout filtre
-- Hibernate. Aucune FK posée : Site vit dans le module etablissement, et
-- affectations_etablissement (module identite) ne référence déjà aucune
-- entité etablissement par FK (etablissement_id lui-même n'en a pas, US-04
-- lot T-04/T-05).
ALTER TABLE affectations_etablissement
    ADD COLUMN site_id UUID;

ALTER TABLE affectations_etablissement_aud
    ADD COLUMN site_id UUID;

-- 3) Jeton d'activation de compte : porte le hash du mot de passe temporaire
-- à usage unique remis à un nouveau compte (ou régénéré), jamais le mot de
-- passe en clair. Le modèle reste volontairement agnostique du canal de
-- remise (in-app aujourd'hui, email au Sprint 10) : aucune colonne ne
-- suppose une remise de la main à la main.
CREATE TABLE jetons_activation_compte (
    id                  UUID          PRIMARY KEY,
    utilisateur_id      UUID          NOT NULL REFERENCES utilisateurs (id),
    mot_de_passe_hache  VARCHAR(255)  NOT NULL,
    date_expiration     TIMESTAMPTZ   NOT NULL,
    date_consommation   TIMESTAMPTZ,
    actif               BOOLEAN       NOT NULL DEFAULT TRUE,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ   NOT NULL,
    date_modification   TIMESTAMPTZ   NOT NULL,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255)
);

CREATE INDEX idx_jeton_activation_utilisateur ON jetons_activation_compte (utilisateur_id);

CREATE TABLE jetons_activation_compte_aud (
    id                  UUID     NOT NULL,
    rev                 BIGINT   NOT NULL REFERENCES revisions (rev),
    revtype             SMALLINT NOT NULL,
    utilisateur_id      UUID,
    mot_de_passe_hache  VARCHAR(255),
    date_expiration     TIMESTAMPTZ,
    date_consommation   TIMESTAMPTZ,
    actif               BOOLEAN,
    date_desactivation  TIMESTAMPTZ,
    date_creation       TIMESTAMPTZ,
    date_modification   TIMESTAMPTZ,
    cree_par            VARCHAR(255),
    modifie_par         VARCHAR(255),
    PRIMARY KEY (id, rev)
);
