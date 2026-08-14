# ADR-0004 — Intégration et déploiement continus

**Statut** : ⚠️ REMPLACÉ par ADR-0007 (Render) — conservé pour l'historique
**Ancien statut** : accepté · **Date** : août 2026

## Stratégie de branches

Volontairement minimale — une seule personne ou une petite équipe.

```
feat/us-08-matricule ──PR──> main ──auto──> RECETTE
                                  │
                              tag v1.2.0 ──manuel + approbation──> PRODUCTION
```

- `main` est toujours déployable et reflète en permanence la recette
- Aucun commit direct sur `main` : protection de branche, CI verte obligatoire
- La production se déploie depuis un **tag**, jamais depuis une branche : on
  sait exactement ce qui tourne, et on peut redéployer un tag antérieur

Pas de branche `develop`. Pour un projet de cette taille, elle ajoute une
étape de synchronisation sans rien résoudre.

## Trois workflows

| Fichier | Déclencheur | Rôle |
| :--- | :--- | :--- |
| `ci.yml` | pull request | Bloque la fusion si quoi que ce soit échoue |
| `deploy-recette.yml` | push sur `main` | Déploiement automatique |
| `deploy-production.yml` | manuel + tag | Déploiement avec approbation humaine |

## Décisions et leurs raisons

### Les migrations tournent dans un job séparé, avant le déploiement

`spring.flyway.enabled=false` en recette et production. Flyway est invoqué par
le pipeline, pas au démarrage de l'application.

**Pourquoi** : si une migration échoue au démarrage de l'application, on se
retrouve avec un service à moitié démarré contre un schéma à moitié migré, et
plus rien ne fonctionne. Dans un job séparé, une migration qui échoue laisse
l'ancienne version intacte et en service. C'est aussi la seule façon de gérer
proprement plusieurs instances : deux applications démarrant simultanément
tenteraient de migrer en même temps.

Flyway reste actif au démarrage en développement local et dans les tests, où
la simplicité prime.

### Les migrations doivent être rétrocompatibles

Pendant un déploiement, l'ancienne version du code tourne quelques minutes
contre le **nouveau** schéma. Une migration qui supprime une colonne encore
lue par l'ancien code provoque une panne immédiate.

Règle : toute évolution destructive se fait en deux versions successives.
Version N ajoute la nouvelle colonne et écrit dans les deux ; version N+1
supprime l'ancienne. Jamais dans le même déploiement.

**Il n'existe pas de retour arrière pour une migration.** On avance, on ne
recule pas : si une migration a mal tourné, la correction est une nouvelle
migration, pas une annulation. D'où la sauvegarde systématique avant
migration en production.

### Le retour arrière du code passe par l'échange d'emplacements

Azure App Service permet de déployer sur un emplacement de préproduction, de
le vérifier, puis de l'échanger avec la production. L'échange est quasi
instantané et réversible.

Conséquence : **le code peut revenir en arrière, la base non.** C'est
exactement pour cela que les migrations doivent être rétrocompatibles — c'est
ce qui rend le retour arrière du code possible.

### Authentification par OIDC, sans secret durable

Aucun mot de passe de service Azure n'est stocké dans GitHub. Le workflow
obtient un jeton éphémère via un credential fédéré Entra ID. Rien à faire
tourner, rien qui puisse fuiter.

Les identifiants Azure (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`,
`AZURE_SUBSCRIPTION_ID`) ne sont pas sensibles en eux-mêmes ; ils sont
néanmoins placés en secrets par habitude d'hygiène.

### La CI vérifie que le contrat OpenAPI et les types générés concordent

Le workflow régénère les types depuis le contrat exposé par le backend et
échoue si le résultat diffère de ce qui est committé.

**Pourquoi** : sans ce garde-fou, un développeur qui renomme un champ de DTO
côté backend sans relancer `npm run api:generate` obtient un frontend qui
compile parfaitement contre un contrat périmé. Le typage cesse alors de
protéger quoi que ce soit. Cette vérification est ce qui rend le pari
TypeScript effectif (ADR-0001).

### Le test d'isolation multi-établissement est bloquant

Le test générique défini en T-05 (ADR-0002) fait partie de `mvn verify` et
bloque donc toute fusion. Une fuite entre établissements ne doit jamais
pouvoir atteindre la recette.

## Points d'attention propres à ce projet

**Une migration touche tous les établissements simultanément.** Avec une base
unique et une colonne discriminante (ADR-0002), il n'existe aucun déploiement
progressif par établissement. Toute migration est un événement global.

**Fenêtre de déploiement.** Les utilisateurs sont en Afrique de l'Ouest
(UTC+0). Déployer en production hors des heures de classe — tôt le matin,
en soirée, ou le week-end. Une bascule d'emplacement dure quelques secondes,
mais une migration sur une grosse table peut durer plusieurs minutes.

**Période de rentrée.** Les inscriptions concentrent l'essentiel de l'activité
sur quelques semaines. Éviter tout déploiement non critique durant cette
période, ou prévoir un gel des livraisons.

## À ajouter plus tard

Analyse statique de sécurité des dépendances, tests de bout en bout sur la
recette, notification Slack ou email des déploiements, et suivi applicatif
(Application Insights). Aucun n'est nécessaire au Sprint 0.
