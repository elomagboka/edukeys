# ADR-0007 — Hébergement et déploiement sur Render

**Statut** : accepté · **Date** : août 2026
**Remplace** : ADR-0003 (hébergement Azure) et ADR-0004 (CI/CD GitHub Actions
vers Azure)

## Décision

Render, région **Frankfurt**, pour l'API, le frontend et PostgreSQL.
Deux environnements : recette et production, isolés.

---

## Pourquoi ce changement

Azure fonctionnait, mais son coût réel n'était pas financier — il était
opérationnel. Le déploiement exigeait un registre d'images, une identité
fédérée Entra ID, des environnements GitHub, l'ouverture et la fermeture
dynamique de règles de pare-feu pour l'IP du runner, et une bascule
d'emplacements. Soit environ trois heures de configuration initiale et une
surface de panne importante, pour une équipe d'une personne.

Sur Render, la même chose tient dans un fichier `render.yaml` versionné avec
le code.

**Le point technique décisif** : la commande de pré-déploiement de Render
exécute les migrations juste avant qu'une nouvelle version passe en service,
depuis le réseau privé. La règle « migrations avant déploiement, dans une
étape séparée » (héritée d'ADR-0004) est donc nativement supportée, sans
exposition réseau et sans orchestration à écrire.

## Architecture

| Service Render | Rôle |
| :--- | :--- |
| `edukeys-api-recette` | API Spring Boot (Docker), déploiement automatique sur `main` |
| `edukeys-api-prod` | API Spring Boot (Docker), déploiement **manuel** |
| `edukeys-web-recette` | Frontend React (site statique) |
| `edukeys-web-prod` | Frontend React (site statique) |
| `edukeys-db-recette` | PostgreSQL |
| `edukeys-db-prod` | PostgreSQL |

Bases séparées entre recette et production. Aucune exception : une erreur de
manipulation sur des données scolaires réelles ne se répare pas.

## Répartition des responsabilités

**GitHub Actions garde le contrôle qualité** : tests backend, tests frontend,
vérification du typage, lint, et surtout la vérification que les types générés
correspondent au contrat OpenAPI (ADR-0001). Rien de tout cela ne migre vers
Render.

**Render gère le déploiement** : construction de l'image, migrations,
publication, vérification de santé.

Cette séparation est volontaire. La CI protège la branche ; le déploiement est
un problème d'infrastructure. Les mélanger était précisément ce qui rendait la
configuration Azure lourde.

## Migrations

`spring.flyway.enabled=false` en recette et production. Flyway est invoqué par
la commande de pré-déploiement, jamais au démarrage de l'application.

**La règle de rétrocompatibilité devient plus importante qu'avec Azure**, pas
moins. Sur App Service, la bascule d'emplacements permettait un retour arrière
du code en quelques secondes. Sur Render, revenir en arrière signifie
redéployer une version antérieure, ce qui prend quelques minutes.

Donc, sans changement : toute suppression de colonne se fait en deux versions.
La version N ajoute et écrit dans les deux ; la version N+1 supprime.

## Production : deux verrous

1. `autoDeploy: false` sur les services de production. Un merge sur `main` ne
   déclenche jamais la production.
2. Un workflow GitHub manuel, soumis à l'approbation de l'environnement
   `production`, qui déclenche le déploiement via le crochet de Render.

Le déclenchement passe par GitHub plutôt que par le tableau de bord Render
pour une raison : cela laisse une trace horodatée, associée à un tag et à un
approbateur. Un déploiement lancé depuis une interface web ne laisse rien.

## Ce que nous perdons, et comment le compenser

**Le retour arrière instantané.** Compensé par la rétrocompatibilité stricte
des migrations et par une sauvegarde avant chaque déploiement de production.

**La résilience multi-région.** Render n'a qu'une région européenne, Frankfurt.
Un incident sur cette région interrompt le service. Il faut l'assumer : à
notre échelle, Azure aurait offert la *possibilité* du multi-région sans que
nous l'ayons jamais mise en œuvre. La compensation est organisationnelle —
prévoir un message d'information aux établissements et un canal de secours,
pas une architecture redondante que nous ne saurions pas exploiter.

**La granularité de configuration.** Render fait des choix à notre place.
C'est le but.

## Point non négociable : les sauvegardes

L'application traite des dossiers d'élèves, des notes et des données
financières. Le plan PostgreSQL retenu en production doit offrir une
restauration à un instant donné, quel qu'en soit le surcoût. C'est le seul
poste où l'économie n'a pas sa place.

À vérifier également : une **sauvegarde exportée hors de Render**, au moins
mensuelle. Une plateforme peut fermer un compte, subir un incident, ou changer
de politique. Une copie chiffrée ailleurs est une assurance à quelques euros.

## Ce qui ne change pas

Le code. PostgreSQL, Docker, Flyway, Spring Boot, le contrat OpenAPI, la
structure du dépôt : rien n'est spécifique à Render. Les décisions des
ADR-0001, 0002, 0005 et 0006 sont intactes.

C'est le bénéfice d'avoir choisi des standards. Si Edukeys atteint une taille
justifiant une infrastructure plus riche, la migration coûtera quelques jours
de configuration, pas une réécriture.

## Latence

Inchangé par rapport à ADR-0003. Les utilisateurs sont à environ 100 ms du
serveur, Frankfurt n'ajoutant que quelques millisecondes par rapport à Paris.
Deux latences distinctes à ne pas confondre :

**Navigateur ↔ serveur : environ 100 ms.** C'est elle qui se voit. Chaque
appel d'API supplémentaire déclenché par un écran coûte un dixième de seconde.
D'où les règles : un écran doit idéalement tenir en un seul appel, sans
cascade de requêtes dépendantes, et les référentiels stables (niveaux,
classes, matières) sont mis en cache longuement côté client.

**Application ↔ base : moins d'une milliseconde**, puisqu'elles sont dans la
même région. C'est la seule latence que nous maîtrisons, d'où l'obligation de
les colocaliser.

### Requêtes en cascade côté base

Un piège classique de JPA : afficher 40 élèves avec le nom de leur
responsable légal déclenche une requête pour la liste, puis une requête par
élève au fil de la boucle — 41 requêtes au lieu de 2. Le code a l'air normal,
rien ne signale que chaque tour de boucle touche la base.

Contrairement à ce que suggérait l'ADR-0003, ce n'est **pas** un problème de
latence perçue : 41 requêtes à moins d'une milliseconde restent
imperceptibles pour un utilisateur seul.

Le problème est la charge. Trente enseignants faisant l'appel à 8 h
transforment ces 41 requêtes en 1 230, ce qui sature le pool de connexions
d'un PostgreSQL de petite taille. Le symptôme n'apparaît jamais en
développement, où l'on est seul, et toujours en production, à l'heure de
pointe.

C'est pourquoi l'agent `reviewer` classe ces requêtes en cascade parmi les
défauts **bloquants** : elles doivent empêcher la fusion, et non figurer dans
une liste de remarques secondaires. La correction est simple quand elle est
faite tôt (`@EntityGraph`, jointure explicite, ou projection) et coûteuse
quand elle se découvre sous charge.

### Résilience

Les coupures de câbles en Afrique de l'Ouest sont récurrentes : prévoir des
délais d'expiration généreux, des tentatives de reprise, et des messages
d'erreur explicites plutôt que des écrans figés.

## Point réglementaire

Inchangé : données personnelles de mineurs togolais hébergées hors du Togo,
chez un prestataire américain. À vérifier auprès de l'autorité togolaise de
protection des données avant le premier établissement en production. Ce point
était identique avec Azure.
