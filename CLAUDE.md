# Edukeys — Contexte projet

Application de gestion scolaire éditée par **Nova Digital** (Togo).
**Edukeys** est le nom du produit : il doit
apparaître tel quel dans l'interface, la documentation d'API et les emails.
Ne jamais le remplacer par une description générique du type « gestion
scolaire » dans du texte visible par l'utilisateur.

> Ce fichier est lu à CHAQUE session Claude Code. Il doit rester court (< 150 lignes).
> Tout ce qui est long va dans `docs/` et n'est lu que sur demande explicite.

## Stack

- **Backend** : Java 21 + Spring Boot 3.5.x, Maven, Spring Data JPA, Spring Security (JWT), MapStruct, SpringDoc OpenAPI. Java 21. Ne pas modifier sans décision explicite — le passage non tracé à Java 25 a rendu ArchUnit silencieusement inopérant.
- **BDD** : PostgreSQL 18 — Docker en local, PostgreSQL managé Render en recette et production. Migrations Flyway.
- **Hébergement** : Render, région Frankfurt. Voir `docs/adr/0007-hebergement-render.md`.
- **Frontend** : React 19 + **TypeScript strict**, Vite, TanStack Query, Ant Design — voir `frontend/CLAUDE.md` et `docs/adr/0001-stack-frontend.md`
- **Tests** : JUnit 5 + AssertJ + Testcontainers (intégration), MockMvc (web)

## Structure

Monorepo : `backend/` (Spring Boot) et `frontend/` (React). Les modules
frontend reflètent exactement les modules backend.

```
backend/src/main/java/tg/novadigital/edukeys/
  common/          # exceptions, config, sécurité, audit, utils partagés
  etablissement/   # US-00
  academique/      # US-01, 02, 03, 05  (année, niveau, cycle, filière, classe, matière, période)
  identite/        # US-04             (users, rôles, RBAC)
  admission/       # US-06, 07, 08     (pré-inscription, dossiers, matricule)
  eleve/           # US-09, 10, 11, 12 (dossier, responsables, mouvements, recherche)
  pedagogie/       # US-13 à US-20     (affectations, EDT, présences, devoirs, notes, bulletins)
  finance/         # US-21 à US-26
  portail/         # US-27 à US-32
  reporting/       # US-33 à US-35
```

Chaque module suit la même arborescence interne :
`domain/` (entités JPA) · `repository/` · `service/` · `web/` (controller + DTO) · `mapper/`

## Règles d'architecture (non négociables)

1. **Aucune dépendance croisée entre modules métier.** Un module n'importe que `common/` et son propre package. Les échanges inter-modules passent par une interface exposée dans le module fournisseur.
2. **Multi-établissement** : toute entité métier porte un `etablissement_id`,
   sans exception (y compris niveaux, filières et matières). Le filtrage est
   automatique (filtre Hibernate + contexte de sécurité), jamais recopié dans
   une requête. **Requêtes natives interdites sur les entités métier** : elles
   échappent au filtre. Voir `docs/adr/0002-multi-etablissement.md`.
3. **Identifiants en UUID version 7** (ordonné dans le temps), jamais `Long`
   auto-incrémenté ni UUID v4. La v7 conserve la localité d'insertion dans
   l'index tout en restant imprévisible de l'extérieur. Vérifier que
   `@UuidGenerator(style = VERSION_7)` existe dans la version d'Hibernate
   embarquée ; sinon, générateur personnalisé.
4. **Pas de suppression physique.** Désactivation logique via
   `EntiteDesactivable` (`actif`, `date_desactivation`) — exigence explicite du
   backlog (US-11). Les contraintes d'unicité sur ces entités sont des **index
   partiels** (`WHERE actif = true`), sinon une adresse email libérée reste
   bloquée à jamais.
   Les repositories métier étendent `BaseRepository<T, ID>` (`@NoRepositoryBean`,
   dérivée de `Repository`), **jamais `JpaRepository`** : cette dernière expose
   `delete`, `deleteAll` et `deleteById`, ce qui rendrait la règle
   contournable. **Ni `JpaSpecificationExecutor`**, qui expose
   `delete(Specification)` — une suppression en masse traduite directement en
   DELETE SQL, sans synchroniser le contexte de persistance : elle
   court-circuite donc l'audit Envers et le filtre multi-établissement. Les
   méthodes par spécification (`findOne`, `findAll`, `count`, `exists`) sont
   redéclarées à la main dans `BaseRepository`, en lecture seule. En Java une
   méthode héritée ne se retire pas — la seule issue est de ne pas hériter.
   Les tests se nettoient par rollback transactionnel
   (`@Transactional` + MockMvc), pas par suppression.
5. **Tout endpoint accessible sans authentification est limité en débit** —
   `/auth/login`, `/auth/refresh`, réinitialisation de mot de passe,
   pré-inscription publique (US-06). Sans cela, un attaquant teste des milliers
   de mots de passe par minute. Limitation par compte **et** par IP, avec
   attente croissante plutôt que verrouillage définitif (un verrouillage dur
   permet de bloquer volontairement le compte d'un directeur).
6. **Historisation** : les entités marquées `@Audited` (Hibernate Envers) — US-07, 10, 11, 13, 18, 19 l'exigent.
7. **Les entités JPA ne sortent jamais des controllers.** Toujours un DTO + mapper MapStruct.
8. **Toute règle métier est dans le service, jamais dans le controller ni le repository.**
9. **Sites et annexes** : `site_id` organise l'intérieur d'un établissement
   (classes, salles, inscriptions, séances, caisses). Ce n'est **pas** un
   second niveau de sécurité : il n'entre jamais dans le filtre Hibernate, et
   la direction voit tous ses sites. Voir `docs/adr/0005-sites-et-annexes.md`.
10. **Deux latences à distinguer.** Navigateur → serveur : ~100 ms (Togo →
   Frankfurt) — chaque appel d'API supplémentaire se voit, donc un écran =
   idéalement un appel, sans cascade. Application → base : < 1 ms, mais une
   liste de 40 élèves qui déclenche 41 requêtes (relation `LAZY` parcourue en
   boucle) s'effondre dès que trente utilisateurs sont simultanés. Les deux
   sont **bloquants** en revue, pas des optimisations pour plus tard.
11. **Tout endpoint de données métier est gardé par une permission explicite** `(hasAuthority)`, 
   jamais par `isAuthenticated()` ni par le défaut `anyRequest().authenticated()`.
   `SUPER_ADMIN` ne porte aucune permission métier (ADR-0002): c'est cette absence qui l'exclut et
   elle n'opère que si l'endpoint exige une permission nommée.

## Conventions

- Entités au singulier français (`Eleve`, `AnneeScolaire`), tables en `snake_case` pluriel (`eleves`)
- Endpoints REST : `/api/v1/<ressource-au-pluriel>`
- Un commit = une US ou une sous-tâche. Message : `feat(US-08): génération matricule`
- Branche : `feat/us-08-matricule`, fusionnée dans `main` par pull request
- Jamais de commit direct sur `main`
- **Migrations Flyway rétrocompatibles** : jamais de suppression de colonne
  dans la même version que le code qui cesse de l'utiliser. Voir
  `docs/adr/0004-cicd-deploiement.md`.

## Environnement de développement

Windows en local, Linux en conteneur pour la recette et la production.
Attention à la casse des noms de fichiers : ignorée par Windows, significative
sous Linux. Voir `docs/SETUP-WINDOWS.md`.

## Migrations et données de test

- `db/migration` : schéma applicatif, appliqué dans **tous** les environnements
- `db/testdata` : données de démonstration, chargé uniquement par les profils
  `local` et `test` via `spring.flyway.locations`

Ne jamais placer de données de démonstration dans `db/migration` : une
migration versionnée s'applique aussi en production, et l'en retirer demande
une migration destructive.

## Commandes

```bash
mvn -q test                    # tests unitaires
mvn -q verify                  # + tests d'intégration
mvn spring-boot:run            # démarrage local
mvn flyway:migrate             # migrations
```

## Notifications

Quatre canaux : in-app, email, SMS (V1) et push (phase mobile). Le code métier
appelle `Notificateur` et ne connaît jamais le canal. Les gabarits SMS sont
rédigés **sans accents** (au-delà de l'alphabet GSM-7, un SMS passe de 160 à
70 caractères et la facture double). Voir `docs/adr/0006-notifications.md`.

## Définition de "Terminé" (DoD)

Une US n'est finie que si : entités + migration Flyway + service + endpoints + DTO/mapper
+ tests unitaires du service + un test d'intégration du endpoint principal + doc OpenAPI à jour.

## Backlog

Le planning des sprints est dans `docs/PLANNING.md` (il fait autorité sur
l'ordre des US, pas le backlog).

Le backlog complet est dans `docs/backlog.md`. **Ne le lis pas en entier.**
Pour travailler sur une US, lis uniquement sa section (`grep -A 20 "US-08" docs/backlog.md`).
