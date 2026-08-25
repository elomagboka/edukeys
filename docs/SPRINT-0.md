# SPRINT 0 — Socle technique

**Durée** : 1 semaine · **US livrées** : US-00 (seule) · **Objectif** : que toute
US suivante n'ait plus qu'à écrire de la logique métier.

Le Sprint 0 ne produit presque aucune fonctionnalité visible. Il produit des
fondations dont chacune des 35 US restantes hérite gratuitement. Codée au
sprint 6, la même chose oblige à réécrire six modules.

---

## T-00 — Figer les décisions (30 min, sans code)

À écrire dans `CLAUDE.md` avant toute ligne de code.

**Versions.** Spring Boot **3.5.16** (dernier patch de la branche), Java 25.
Pas 4.1.x : sortie en juin 2026, l'écosystème n'a pas fini de suivre. Spring
Boot 4 a basculé sur Jackson 3 et Jakarta EE 11, ce qui a cassé une partie
des bibliothèques tierces pendant plusieurs mois — SpringDoc en particulier.
La branche 4.0 a eu le temps de se stabiliser, la 4.1 non.

Si tu préfères zéro friction : Spring Boot 3.5.x + SpringDoc 2.x, combinaison
massivement déployée. C'est le choix prudent, au prix d'une migration plus
tard.

**Règle** : versions figées explicitement dans le `pom.xml` parent, jamais de
`LATEST`. Une seule montée de version à la fois, jamais en cours de sprint.

**Multi-établissement** : tranché — **multi**, par colonne discriminante.
Voir `docs/adr/0002-multi-etablissement.md`, qui fixe aussi le rôle
`SUPER_ADMIN`, la relation N-N utilisateur/établissement et le format des
matricules. Ces choix impactent T-04, T-05, T-08 et T-10.

**Hébergement** : tranché — Render, région Frankfurt. Voir
`docs/adr/0007-hebergement-render.md`, qui remplace les ADR-0003 et 0004. En
développement, PostgreSQL reste en Docker local : aucun service cloud n'est
nécessaire avant la recette.

**Frontend** : tranché — React 19 + TypeScript strict, Vite, Ant Design.
Voir `docs/adr/0001-stack-frontend.md`. Impacts sur le Sprint 0 : ajouter
T-11 (socle frontend), et activer CORS dès T-04.

---

## T-01 — Squelette qui démarre (0,5 j)

> **Développement sous Windows** : suis d'abord `docs/SETUP-WINDOWS.md`
> (installation des outils, chemins longs, fins de ligne). Une demi-heure qui
> évite plusieurs heures de dépannage obscur.

Un projet vide mais qui tourne contre une vraie base.

- `pom.xml` parent + module `backend`
- `docker-compose.yml` : PostgreSQL 16 + volume persistant
- Flyway branché, `V1__init.sql` (juste une table `schema_test`)
- `application.yml` + profils `local`, `test`
- Endpoint `/actuator/health` accessible

**Critère de fin** : `docker compose up -d && mvn spring-boot:run` démarre,
Flyway applique la migration, `/actuator/health` répond `UP`. Rien d'autre.

> Ne saute pas cette étape en te disant que tu la feras "en même temps que le
> reste". Un socle qui démarre est le point de référence qui te permettra de
> savoir, plus tard, si c'est ton code ou ta config qui casse.

---

## T-02 — Socle commun (1 j)

Package `common/`. C'est le cœur du sprint.

- `BaseEntity` : `id` (UUID), `actif`, `dateCreation`, `dateModification`,
  `creePar`, `modifiePar` — via `@MappedSuperclass` + JPA Auditing
- Hiérarchie d'exceptions métier : `RessourceIntrouvableException`,
  `RegleMetierViolee`, `ConflitException`, `AccesInterditException`
- `@RestControllerAdvice` global → format d'erreur unique (RFC 7807
  `ProblemDetail`), avec un identifiant de corrélation par requête
- Interface `EntiteDesactivable` + méthode `desactiver()` — **aucun `delete`
  dans tout le projet**
- Logging structuré, avec une règle explicite : aucune donnée personnelle
  d'élève ou de parent dans les logs
- Interface `Notificateur` + énumération des types de notification (ADR-0006).
  Aucune implémentation de canal ici — juste l'abstraction, pour qu'aucun
  service métier n'appelle jamais un fournisseur d'email en direct

**Critère de fin** : un endpoint de test qui lance chaque exception renvoie le
bon code HTTP et le bon corps JSON.

---

## T-03 — Pagination et recherche standardisées (0,5 j)

US-12 demande une recherche multicritères, et six autres US demandent des
listes. Fais-le une fois, proprement.

- Enveloppe de réponse paginée maison (ne renvoie jamais un `Page` Spring
  directement : sa sérialisation est instable entre versions)
- Support `?page=&size=&sort=` avec des bornes (taille max 100)
- Base de `Specification` JPA réutilisable pour les filtres dynamiques
- Utilitaire d'export Excel générique (US-12, US-24, US-35 en ont besoin)

**Critère de fin** : un contrôleur de démo pagine, trie et filtre une entité
factice, avec un test d'intégration.

---

## T-04 — Sécurité et RBAC (1,5 j)

C'est US-04, mais son socle appartient au Sprint 0 : sans lui, aucune US
suivante ne peut poser de `@PreAuthorize`.

- Spring Security, authentification JWT (access + refresh)
- Entités `Utilisateur`, `Role`, `Permission`, `AffectationEtablissement`
- Relation **N-N** utilisateur ↔ établissement, le rôle étant porté par
  l'affectation (ADR-0002)
- Les six rôles du backlog : Admin, Direction, Gestionnaire, Enseignant,
  Parent, Élève — plus `SUPER_ADMIN`, hors périmètre établissement
- Email unique **globalement**, pas par établissement
- JWT portant l'établissement actif + endpoint de bascule d'établissement
- Hachage BCrypt, endpoints `/api/v1/auth/login` et `/refresh`
- Sécurisation par défaut : **tout est interdit sauf déclaré ouvert**
- Désactivation logique d'un compte (US-04)

**Critère de fin** : deux utilisateurs de rôles différents, un endpoint
protégé, un test qui prouve que l'un passe et l'autre reçoit un 403.

> **Hors périmètre de T-04, mais bloquant avant tout déploiement en
> production** : la limitation de débit sur les endpoints d'authentification.
> Absente du backlog d'origine. `/auth/login` exposé sans limitation permet de
> tester des milliers de mots de passe par minute contre un compte connu.
>
> Suivie dans l'issue **#58**, porteuse de l'étiquette
> `securite:bloque-deploiement`. Le workflow `deploy-production.yml` refuse de
> déployer tant qu'une issue portant cette étiquette reste ouverte — ce n'est
> donc pas une intention mais une porte fermée.
>
> Ce garde-fou couvre la production, pas la recette. **Tant que l'issue est
> ouverte, la recette ne doit pas être accessible publiquement** (protection
> par mot de passe au niveau Render). Le risque naît quand l'endpoint devient
> joignable depuis Internet, pas quand il est déployé en production.

> Attention : les rôles Parent et Élève ne sont pas de simples rôles. Un parent
> ne voit que *ses* enfants, un élève que *ses* notes. Ce filtrage par
> propriété des données se conçoit maintenant, pas au sprint 9 quand tu
> ouvriras les portails.

---

## T-05 — Contexte multi-établissement (1 j) · **obligatoire**

- `etablissement_id` sur `EntiteEtablissement`, superclasse dédiée intercalée
  sous `BaseEntity` — voir ADR-0002, « Précision d'implémentation (T-05) »
- Résolution de l'établissement courant depuis le JWT, stockée dans le
  contexte de sécurité
- Filtre Hibernate activé automatiquement sur chaque session
- Remplissage automatique de `etablissement_id` à la persistance
- Ouverture explicite d'un contexte d'établissement pour les traitements
  asynchrones et planifiés, qui n'ont pas de contexte de sécurité
- Désactivation ciblée du filtre pour `SUPER_ADMIN`, sur les seules entités
  établissement et utilisateur — jamais sur les données métier

**Critère de fin** : un **test d'isolation générique** qui parcourt toutes les
entités métier et prouve qu'un utilisateur de l'établissement A n'atteint
aucune donnée de l'établissement B, y compris par accès direct à
l'identifiant. Ce test s'écrit une fois et protège les 35 US suivantes.

Le même test d'isolation prouve aussi qu'un `SUPER_ADMIN` ayant basculé sur un
établissement n'atteint aucune donnée métier de cet établissement : le filtre
n'est désactivé pour lui que sur les entités établissement et utilisateur
(ligne ci-dessus), jamais sur les données métier — la désactivation ciblée
elle-même doit être couverte, pas seulement l'isolation entre deux
établissements ordinaires.

Prouver l'isolation ne suffit pas : il faut aussi prouver qu'on ne peut pas
la contourner. Les trois angles morts du filtre Hibernate (ADR-0002) doivent
être verrouillés par des tests, pas par la vigilance en relecture —
`AucuneDesactivationDuFiltreTest`, `AucuneRequeteNativeSurEntiteMetierTest`,
`IsolationUtilisateursTest` (`Utilisateur` n'est pas filtrée : son
cloisonnement repose entièrement sur `AffectationEtablissement`) et
`UtilisateurService` cadré par affectation. Suivi dans l'issue **#61**.

> C'est le test le plus important du sprint. Si ce filtrage est automatique,
> aucun développeur ne peut l'oublier. S'il est manuel, quelqu'un l'oubliera.

---

## T-06 — Audit et historisation (0,5 j)

Sept US du backlog exigent explicitement l'historisation (US-07, 10, 11, 13,
18, 19, et la traçabilité de US-22).

- Hibernate Envers configuré, tables `_aud`
- `@Audited` sur `BaseEntity`
- Endpoint générique de consultation d'historique d'une entité

**Critère de fin** : modifier une entité crée une révision consultable, avec
l'auteur et la date.

---

## T-07 — Documentation d'API (0,5 j)

- SpringDoc, Swagger UI accessible en `local` seulement
- Schéma de sécurité JWT déclaré (bouton *Authorize* fonctionnel)
- Convention d'annotation des contrôleurs, à documenter dans `CLAUDE.md`
- Export du fichier `openapi.json` au build (c'est lui qui alimente
  `npm run api:generate` côté frontend)

> Avec TypeScript, cette tâche cesse d'être de la documentation pour devenir
> une pièce d'infrastructure : c'est le contrat OpenAPI qui fait que le build
> frontend casse quand un DTO backend change. Soigne les annotations,
> notamment les champs obligatoires et les types nullables — un contrat
> imprécis produit des types permissifs, donc inutiles.

**Si SpringDoc résiste** : c'est le composant le plus sensible aux versions.
Ne bloque pas le sprint dessus. Deux paliers de repli : rétrograder d'un patch
Spring Boot, ou repousser T-07 en fin de sprint. Ne fais jamais l'inverse —
changer la version de Spring Boot pour faire plaisir à SpringDoc casse
Hibernate et Security en cascade.

---

## T-08 — Socle de tests (1 j)

Sans ça, chaque US réinvente ses fixtures et tu perds une demi-journée par
sprint.

- Testcontainers PostgreSQL, conteneur réutilisé entre classes de test
- `IntegrationTestBase` : contexte Spring, base propre entre tests
- Fabriques de données de test : `unEtablissement()`, `unUtilisateur(role)`
- **Deux établissements systématiquement** dans le jeu de test : c'est la seule
  façon de rendre les fuites d'isolation détectables
- Utilitaire d'authentification dans les tests MockMvc
- Jeu de données de démo (profil `local`) : 1 établissement, 1 an, 6 rôles

**Critère de fin** : écrire un test d'intégration authentifié prend moins de
dix lignes.

---

## T-09 — Intégration et déploiement continus (0,5 j)

Le blueprint `render.yaml`, le `backend/Dockerfile` et les workflows sont déjà
dans le dépôt. Cette tâche consiste à créer les services et à brancher les
secrets : suivre `docs/RENDER-SETUP.md` pas à pas.

- Création des six services depuis le blueprint (deux bases, deux API, deux
  frontends), région Frankfurt
- Secret JWT de production saisi à la main
- Crochets de déploiement et clé d'API Render récupérés
- Environnement GitHub `production` avec approbation obligatoire
- Protection de la branche `main`

**Critère de fin** : une PR volontairement cassée est refusée par la CI, et
une fusion sur `main` déploie automatiquement en recette.

> Fais cette tâche avant d'avoir beaucoup de code. Monter un pipeline sur un
> projet quasi vide prend deux heures ; le monter sur vingt US déjà écrites
> revient à déboguer le pipeline et le code en même temps.

---

## T-10 — US-00 en tranche verticale (1 j)

Première vraie US, choisie parce qu'elle traverse tout le socle et qu'elle
manque à ton planning.

Établissement complet : entité, migration, service, CRUD REST, upload du logo,
DTO, mappers, tests, permissions `SUPER_ADMIN`, historisation, **modèle
d'initialisation** du référentiel (niveaux, cycles, matières courantes), et
entité `Site` avec création automatique du site principal (ADR-0005). Rien de nouveau
techniquement — uniquement l'assemblage de T-01 à T-08.

**Critère de fin** : US-00 satisfait sa définition de terminé, et le code
produit sert de **modèle de référence** pour les 35 US suivantes.

> C'est le vrai livrable du sprint. À partir de là, tu pourras dire à Claude
> Code : « implémente US-01 en suivant exactement le pattern du module
> `etablissement` ». Le gain de tokens et de qualité est considérable.

---

## T-11 — Socle frontend (1 j) · *en parallèle de T-08 à T-10*

- Vite + React 19 + React Router, structure `features/` miroir du backend
- `tsconfig.json` en `strict: true` dès le départ (l'assouplir plus tard est
  facile, le durcir sur 200 fichiers ne l'est pas)
- `api/client.ts` : `openapi-fetch`, injection du JWT, refresh automatique,
  mapping des erreurs RFC 7807 du backend vers des messages utilisateur
- `npm run api:generate` branché sur l'OpenAPI de T-07
- TanStack Query configuré (durées de cache, retry, invalidation)
- Ant Design + locale FR + thème de base
- Layout applicatif : barre latérale, en-tête, fil d'Ariane, menu filtré par rôle
- Écran de connexion fonctionnel contre T-04
- Composants transverses : `DataTable` (pagination serveur, générique et typé),
  `PageHeader`, `ConfirmDialog`, `FormModal`
- ESLint strict (`any` bloquant), Vitest + MSW
- CI : `typecheck`, `lint`, `test`, `build` — tous bloquants

**Critère de fin** : se connecter avec un compte Admin, voir le menu, et lister
les établissements de T-10 dans un tableau paginé.

> `DataTable` est le composant le plus rentable du projet : il sera réutilisé
> par une quinzaine d'écrans. Prends le temps de bien le faire ici — pagination
> serveur, tri, filtres, état vide, état d'erreur.

---

## Déroulé avec Claude Code

Une tâche = une session. `/clear` entre chaque.

```bash
claude

> Lis docs/SPRINT-0.md et implémente T-01. Arrête-toi quand
  /actuator/health répond UP contre le Postgres de docker-compose.

> /point        # journalise
> /clear

> Lis docs/SPRINT-0.md, section T-02, et le JOURNAL. Implémente T-02.
```

Deux exceptions à la règle du sous-agent : **T-01 et T-02 se font en session
principale**, sans déléguer. Tu dois voir et valider ces choix toi-même — ce
sont eux que tu vas répéter 35 fois. À partir de T-03, la délégation
(`architecte` → `dev-backend` → `testeur` → `reviewer`) prend le relais.

---

## Ce que le Sprint 0 ne fait PAS

Ni notifications, ni génération PDF, ni tableaux de bord, ni cache, ni
messagerie, ni frontend. Ces briques arrivent avec la première US qui en a
besoin — les construire d'avance, c'est deviner des besoins que tu ne connais
pas encore.

L'abstraction `Notificateur` fait exception : elle est en T-02, sans aucun
canal implémenté. Deux heures maintenant contre plusieurs jours de
rétro-adaptation une fois que quinze services appelleront un service d'email
en direct (ADR-0006).
