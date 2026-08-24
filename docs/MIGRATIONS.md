# Migrations — patrons sûrs en production

Le *pourquoi* est dans `docs/adr/0007-hebergement-render.md` : les migrations
tournent dans la commande de pré-déploiement, avant que la nouvelle version
prenne le service, et il n'existe **aucun retour arrière** pour une migration.
Ce fichier est le *comment*.

Deux règles gouvernent tout le reste :

1. **Rétrocompatibilité.** Pendant un déploiement, l'ancienne version du code
   tourne quelques minutes contre le nouveau schéma.
2. **Aucun verrou long.** Une migration qui bloque les écritures pendant une
   minute est une interruption de service. Sur une table vide, tout est
   instantané ; les patrons ci-dessous ne servent qu'à partir du moment où il
   y a des données réelles.

---

## Ajouter une clé étrangère

`ADD CONSTRAINT ... FOREIGN KEY` vérifie **toutes** les lignes existantes et
verrouille la table pendant ce temps. Instantané sur une table vide, bloquant
sur cent mille inscriptions.

```sql
-- 1. Pose la contrainte sans vérifier l'existant : verrou bref
ALTER TABLE inscriptions
    ADD CONSTRAINT fk_inscription_eleve
    FOREIGN KEY (eleve_id) REFERENCES eleves (id)
    NOT VALID;

-- 2. Vérifie l'existant sans bloquer les écritures
ALTER TABLE inscriptions VALIDATE CONSTRAINT fk_inscription_eleve;
```

`NOT VALID` ne concerne que les lignes **déjà présentes** : les nouvelles
insertions sont contrôlées dès l'étape 1.

## Ajouter une colonne obligatoire

Jamais `ADD COLUMN ... NOT NULL` d'un coup sur une table peuplée : l'ancienne
version du code, qui ignore la colonne, échouerait à chaque insertion.

En trois migrations, sur trois versions applicatives :

```sql
-- V(n)   : la colonne apparaît, facultative
ALTER TABLE eleves ADD COLUMN nationalite VARCHAR(50);

-- V(n+1) : remplissage, une fois que le code écrit dedans
UPDATE eleves SET nationalite = 'TG' WHERE nationalite IS NULL;

-- V(n+2) : verrouillage, une fois qu'aucune ligne n'est vide
ALTER TABLE eleves ALTER COLUMN nationalite SET NOT NULL;
```

## Créer un index

`CREATE INDEX` verrouille la table en écriture. Sur une grosse table :

```sql
CREATE INDEX CONCURRENTLY idx_eleves_etablissement_nom
    ON eleves (etablissement_id, nom);
```

Deux contraintes : `CONCURRENTLY` **ne peut pas tourner dans une transaction**,
il faut donc `-- flyway:executeInTransaction=false` en tête du fichier. Et un
index créé ainsi peut finir en état `INVALID` si la création échoue — il faut
alors le supprimer et recommencer.

## Supprimer une colonne

Jamais dans la même version que le code qui cesse de l'utiliser.

```
V(n)   : le code n'écrit plus dedans, ne la lit plus. La colonne reste.
V(n+1) : ALTER TABLE ... DROP COLUMN
```

Entre les deux, un retour arrière du code reste possible.

## Renommer une colonne

Jamais directement — un `RENAME` casse instantanément l'ancienne version.
Ajouter la nouvelle, écrire dans les deux pendant une version, migrer les
lectures, puis supprimer l'ancienne. Quatre versions.

En pratique, se demander d'abord si le renommage vaut ce coût.

## Modifier un type

Même principe : nouvelle colonne, double écriture, bascule des lectures,
suppression. `ALTER COLUMN ... TYPE` réécrit toute la table sous verrou.

---

## Spécificités Edukeys

**`etablissement_id` en tête de tout index métier**, et dans toute contrainte
d'unicité. Sans quoi un code de matière unique dans un établissement
bloquerait les autres (ADR-0002).

**Unicité sur une entité désactivable** : index partiel, jamais contrainte
simple.

```sql
CREATE UNIQUE INDEX uk_matieres_code_actif
    ON matieres (etablissement_id, code) WHERE actif = TRUE;
```

**`TIMESTAMPTZ`, jamais `TIMESTAMP`.** Serveurs à Frankfurt, utilisateurs au
Togo : un horodatage sans fuseau produit une heure de décalage silencieuse sur
les absences, les retards et les échéances.

**Une migration touche tous les établissements simultanément.** Base unique,
colonne discriminante : il n'existe aucun déploiement progressif par
établissement.

**`db/migration` pour le schéma, `db/testdata` en `R__` pour les données de
démonstration.** Les répétables s'exécutent après toutes les versionnées, donc
sans collision de numérotation possible.
