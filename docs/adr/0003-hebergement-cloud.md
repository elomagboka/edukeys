# ADR-0003 — Hébergement et base de données

**Statut** : ⚠️ REMPLACÉ par ADR-0007 (Render) — conservé pour l'historique
**Ancien statut** : accepté · **Date** : août 2026

## Décision

Azure, avec **Azure Database for PostgreSQL Flexible Server**. Application et
base **obligatoirement colocalisées dans la même région**.

---

## Pourquoi pas Neon

Neon avait été envisagé pour sa compatibilité Postgres, réelle. Deux raisons
l'écartent :

**1. Neon n'est plus disponible sur Azure.** Les régions Azure de Neon ont été
dépréciées le 7 avril 2026 ; aucun nouveau projet ne peut y être créé, et les
projets existants doivent migrer avant le 5 octobre 2026. L'intégration native
Azure a été retirée. Les options restantes sont une région AWS de Neon, ou
Databricks Lakebase.

Utiliser Neon sur AWS avec une application sur Azure ferait transiter chaque
requête JPA par l'internet public, entre deux clouds. Une seule page de dossier
élève déclenche facilement dix à vingt requêtes ; à 30 ms de latence chacune,
la page met une seconde à s'afficher — avant même le rendu.

**2. Le modèle serverless ne correspond pas à Spring Boot.** L'intérêt
économique de Neon repose sur le scale-to-zero. Un pool HikariCP maintient des
connexions ouvertes en permanence : la base ne s'endort jamais. On paierait le
tarif serverless sans le bénéfice, en héritant des contraintes du proxy de
connexion.

Neon reste excellent — pour des architectures réellement éphémères. Ce n'est
pas le profil d'un backend Java permanent.

## Pourquoi Azure Database for PostgreSQL Flexible Server

- PostgreSQL natif : Flyway, Hibernate Envers, Testcontainers, `pg_trgm` pour
  la recherche multicritères (US-12) fonctionnent sans adaptation
- PgBouncer intégré, cohérent avec HikariCP
- Colocalisable avec l'application, dans le même réseau virtuel
- Sauvegardes, restauration à un instant donné et haute disponibilité gérées
- Niveau *Burstable* (B-series) suffisant pour démarrer et peu coûteux

---

## Le choix de région : le point le plus structurant

Les utilisateurs sont au Togo. Azure n'a **aucune région en Afrique de
l'Ouest**. Deux candidats :

**South Africa North (Johannesburg)** — géographiquement plus proche, mais
trompeur. Le trafic ouest-africain vers l'Afrique du Sud emprunte les mêmes
câbles sous-marins de la côte ouest (WACS, SAT-3, ACE, MainOne) qui remontent
majoritairement vers l'Europe. Ces câbles ont connu des ruptures multiples
simultanées qui ont dégradé à la fois l'Afrique de l'Ouest et les régions Azure
sud-africaines. La proximité sur la carte ne garantit pas la proximité réseau.

**France Central ou West Europe** — **retenu**. Le trafic depuis Lomé remonte
de toute façon vers l'Europe. Ces régions offrent davantage de services, plus
de diversité de routes, et des tarifs inférieurs à South Africa North.

**Conséquence à assumer** : la latence entre un poste à Lomé et l'application
sera de l'ordre de 80 à 150 ms, variable, avec des épisodes de dégradation
lors des incidents de câbles. Ce n'est pas un défaut de conception, c'est une
donnée du contexte. L'architecture doit en tenir compte.

---

## Conséquences architecturales de la latence

Ces règles ne sont pas des optimisations tardives : elles conditionnent la
qualité perçue de l'application.

1. **Application et base dans la même région, même réseau virtuel.** La
   latence application↔base doit rester sous la milliseconde. C'est la seule
   latence qu'on maîtrise — il serait absurde de la gaspiller.

2. **Le N+1 devient un problème de premier ordre.** Sur un réseau local, 50
   requêtes superflues passent inaperçues. Ici, chaque aller-retour
   supplémentaire du navigateur vers l'application coûte 100 ms. Le
   `reviewer` doit traiter tout N+1 comme bloquant, pas comme mineur.

3. **Minimiser les allers-retours du frontend.** Un écran = idéalement un
   appel d'API. Pas de cascade de requêtes dépendantes. Les endpoints exposent
   des vues agrégées quand c'est ce dont l'écran a besoin.

4. **Cache agressif côté client.** TanStack Query avec des durées de fraîcheur
   longues sur les référentiels (niveaux, classes, matières) : ils changent
   quelques fois par an, pas besoin de les recharger à chaque écran.

5. **Compression et actifs statiques.** Compression activée sur l'API, et le
   frontend servi via un CDN (Azure Front Door) plutôt que depuis la région
   européenne.

6. **Tolérance aux coupures.** Les ruptures de câbles en Afrique de l'Ouest
   sont récurrentes. Prévoir des délais d'expiration généreux, des tentatives
   de reprise, et des messages d'erreur explicites plutôt que des écrans figés.
   À terme, envisager la saisie hors ligne pour l'appel numérique (US-15), qui
   se fait en classe et ne peut pas dépendre d'une connexion stable.

---

## Point réglementaire à vérifier

L'application traitera des données personnelles de mineurs (identité, santé
éventuelle, situation familiale, données financières des parents). Ces données
seraient hébergées hors du Togo.

Le Togo dispose d'un cadre de protection des données personnelles et d'une
autorité de contrôle. **À vérifier avant la mise en production** : obligations
de déclaration, conditions d'un transfert de données hors du territoire,
éventuelles exigences de localisation pour les données scolaires.

Ce point n'est pas technique et ne bloque pas le développement, mais il doit
être traité avant le premier établissement réel — pas après.

---

## Environnements

| Environnement | Base |
| :--- | :--- |
| Développement local | PostgreSQL via Docker Compose |
| Tests automatisés | Testcontainers PostgreSQL |
| Recette | Flexible Server, niveau Burstable |
| Production | Flexible Server, avec sauvegardes et restauration à un instant donné |

Aucune base cloud n'est nécessaire en développement : Docker local est plus
rapide, gratuit, et fonctionne sans connexion. Les migrations Flyway
garantissent la cohérence entre les environnements.

## À décider plus tard (ne bloque pas le Sprint 0)

Le mode d'exécution de l'application (App Service, Container Apps, ou machine
virtuelle) et le stockage des fichiers (Azure Blob Storage pour les pièces
jointes d'admission et les bulletins). Ces choix n'affectent pas le code si
l'application reste conteneurisée et si l'accès au stockage passe par une
abstraction.
