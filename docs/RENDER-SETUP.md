# Mise en place Render — pas à pas

À faire une seule fois, pendant T-09 du Sprint 0. Compter **une à deux
heures**, contre trois sur Azure.

Le blueprint `render.yaml` et le `backend/Dockerfile` sont déjà dans le dépôt.
Ce guide couvre ce qui ne peut pas être versionné : les comptes, les secrets
et les protections GitHub.

---

## 1. Créer les services depuis le blueprint

Sur [render.com](https://render.com), connecte ton compte GitHub, puis
*New → Blueprint* et sélectionne le dépôt Edukeys.

Render lit `render.yaml` et propose de créer six ressources : deux bases, deux
API, deux frontends. Vérifie avant de valider :

- la région est bien **Frankfurt** partout (elle n'est pas modifiable ensuite) ;
- le plan de `edukeys-db-prod` offre la **restauration à un instant donné** —
  c'est le seul poste sur lequel ne pas économiser (ADR-0007).

Le premier déploiement de la recette part tout seul. Il échouera probablement :
c'est normal tant que T-01 n'est pas fait et qu'il n'y a rien à construire.

## 2. Renseigner le secret de production

`JWT_SECRET` est marqué `sync: false` pour la production : il n'est
volontairement pas dans le blueprint, pour ne pas se retrouver versionné.

Génère-le et saisis-le dans *edukeys-api-prod → Environment* :

```bash
openssl rand -base64 48
```

En recette, Render le génère automatiquement — aucune action.

## 3. Récupérer les crochets de déploiement

Pour chaque service de **production** : *Settings → Deploy Hook*. Copie les
deux URL, elles sont secrètes (quiconque les possède peut déclencher un
déploiement).

Récupère aussi une clé d'API dans *Account Settings → API Keys*, et
l'identifiant de la base de production (visible dans son URL).

## 4. Configurer GitHub

*Settings → Environments → New environment*, nommé **production**.

Coche **Required reviewers** et ajoute-toi. C'est ce réglage, et lui seul, qui
produit le bouton d'approbation avant tout déploiement en production.

Puis *Settings → Secrets and variables → Actions* :

**Secrets**

```
RENDER_DEPLOY_HOOK_API    crochet de edukeys-api-prod
RENDER_DEPLOY_HOOK_WEB    crochet de edukeys-web-prod
RENDER_API_KEY            clé d'API Render
RENDER_DB_PROD_ID         identifiant de edukeys-db-prod
```

**Variables**

```
PROD_URL                  https://edukeys-api-prod.onrender.com
```

## 5. Protéger la branche main

*Settings → Rules → New branch ruleset*, appliqué à `main` :

- Require a pull request before merging
- Require status checks to pass → cocher `backend` et `frontend`
- Block force pushes

Sans cette protection, la CI reste facultative et ne protège rien : Render
déploierait en recette du code qui n'a pas passé les tests.

## 6. Vérifier

```bash
git checkout -b test/pipeline
echo "test" >> README.md
git commit -am "test: vérification du pipeline" && git push -u origin test/pipeline
gh pr create --fill
```

Attendu : la CI se lance, la fusion est bloquée tant qu'elle tourne. Après
fusion, Render déploie la recette automatiquement. La production, elle, ne
bouge pas.

---

## Déployer en production

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Puis *Actions → Déploiement production → Run workflow* : saisis le tag et tape
`PRODUCTION`. Le workflow s'arrête sur la demande d'approbation.

Une fois approuvé : sauvegarde → déclenchement Render → construction de
l'image → **migrations Flyway** → publication → vérification de santé.

## En cas d'incident

**L'application ne répond plus.** Dans le tableau de bord Render, onglet
*Events* du service : chaque déploiement réussi offre un *Rollback*. Quelques
minutes, contre quelques secondes avec la bascule d'emplacements d'Azure —
c'est le compromis assumé en ADR-0007.

**Une migration a échoué.** Le déploiement s'est arrêté avant la publication :
l'ancienne version tourne toujours. C'est exactement le comportement voulu.
Corrige la migration, refais un tag, redéploie. Ne tente jamais de « défaire »
une migration à la main en production.

**Le build échoue sur Render mais réussit en local.** Regarde la casse des
noms de fichiers en premier : Windows l'ignore, l'image Linux non.

---

## Trois choses à savoir

**L'URL de base fournie par Render n'est pas au format JDBC.** Render expose
une chaîne `postgres://…` que le pilote JDBC ne comprend pas. Le blueprint
contourne le problème en reconstruisant l'URL à partir des propriétés séparées
(hôte, port, base). Ne remplace pas ce montage par la chaîne de connexion
brute : ça échouera au démarrage avec un message peu clair.

**La commande de pré-déploiement exige un plan payant.** Elle n'est pas
disponible sur le niveau gratuit. Comme c'est elle qui exécute les migrations,
la recette doit être sur un plan payant, même modeste.

**Prévois une sauvegarde hors de Render.** Au moins mensuelle, chiffrée,
ailleurs. Une plateforme peut suspendre un compte ou subir un incident majeur.
Pour des dossiers d'élèves, c'est une assurance à quelques euros.
