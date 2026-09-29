# scripts/

| Script                  | Rôle                                                                                            |
| :---------------------- | :---------------------------------------------------------------------------------------------- |
| `creer-issues.sh`       | Crée jalons, étiquettes et issues GitHub (une seule fois).                                      |
| `mutation-isolation.sh` | Vérifie par mutation que le test d'isolation multi-établissement protège réellement une entité. |

## `mutation-isolation.sh`

### À quoi il sert

`IsolationEtablissementTest` parcourt toutes les fabriques enregistrées dans
`FabriquesEntitesTest`. Qu'il soit vert ne prouve pas qu'il **détecterait** une
fuite sur une entité donnée : un cas peut passer sans rien exercer (C8 sur une
entité dont toutes les colonnes sont `updatable = false` restait vert, US-07).

Le script réduit le registre à la seule fabrique de l'entité, désarme tour à
tour chaque garde du dispositif (ADR-0002), relance les cas C1 à C10, et
compare les échecs obtenus à ceux attendus. Il restaure toujours les fichiers,
y compris sur Ctrl+C ou erreur.

### Cadence : une fois par sprint, pas à chaque US

Chaque exécution dure environ 12 minutes (7 passes du test d'intégration). Sur
la dizaine d'entités filtrées restantes, le lancer à chaque US représenterait
deux heures cumulées — donc il finirait par être sauté par lassitude, ce qui
est la pire façon d'abandonner un garde-fou.

**Cadence retenue : une fois par sprint**, en fin de sprint, sur les seules
entités filtrées ajoutées ou modifiées depuis la dernière exécution.

Trois exceptions, à lancer immédiatement sans attendre la fin du sprint :

- une modification de `RemplisseurEtablissement`, `ArmeurFiltreEtablissement`,
  `GardeContexteEtablissement` ou `ContexteEtablissement` — ces gardes servent
  toutes les entités, une régression y est globale ;
- une modification d'`IsolationEtablissementTest` lui-même, y compris l'ajout
  d'un cas ou l'adaptation d'une fabrique ;
- une entité filtrée qui sort de l'ordinaire : sans colonne modifiable, en
  ajout seul, avec une clé partagée, ou dont la fabrique a des prérequis.

Consigner l'exécution dans `docs/JOURNAL.md` avec la liste des entités
vérifiées. C'est ce qui permet de savoir, au sprint suivant, ce qui reste à
couvrir.

### Quand le lancer

**À chaque nouvelle entité `EntiteEtablissement`**, une fois sa fabrique
enregistrée, avant la PR — et à chaque modification de
`RemplisseurEtablissement`, `ArmeurFiltreEtablissement`,
`GardeContexteEtablissement` ou d'`IsolationEtablissementTest`.

```bash
bash scripts/mutation-isolation.sh DecisionAdmission    # nom simple de l'entité
```

Prérequis : Docker démarré, aucune modification locale dans les fichiers mutés.
Durée : ~12 min (7 exécutions du test). Journaux Maven et rapports Surefire de
chaque ligne : `backend/target/mutation-isolation/<Entite>/`.

### Lire la matrice

```
mutation      C1   C2   C3   C4   C5   C6   C7   C8   C9   C10
temoin        .    .    .    .    .    .    .    .    .    .
filtre        R    R    R    R    R    R    R    R    R    R
filtreSansC0  R    R    R    R    .    .    .    .    .    .
preupdate     .    .    .    .    .    .    .    R    .    .
prepersist    .    .    .    .    R    .    .    .    .    R
garde         .    .    .    .    .    R    .    .    .    .
remplissage   R    R    R    R    .    R    .    R    R    .
```

`R` rouge, `.` vert. Ci-dessus, la matrice attendue, obtenue sur
`DecisionAdmission` (US-07). Le script signale :

- `.!` — **défaut injecté non détecté** : bloquant, le script sort en erreur.
  Le cas ne protège pas cette entité ; corriger le test ou la fabrique.
- `R?` — rouge inattendu : souvent un échec de **préparation** (fabrique qui
  ne sait plus persister ses prérequis), à lire dans le journal de la ligne.
- `?!` — cas absent du rapport (renommé ? compilation cassée ?).

Ce que chaque ligne désarme, et donc ce que chaque cas protège :

| Mutation       | Garde désarmée                                                        | Cas qui doivent rougir                       |
| :------------- | :-------------------------------------------------------------------- | :------------------------------------------- |
| `filtre`       | `enableFilter` dans `ArmeurFiltreEtablissement` (C0 actif)            | tous, **via C0** : ne prouve rien par entité |
| `filtreSansC0` | même chose, garde-fou C0 neutralisé                                   | C1–C4 (lectures)                             |
| `preupdate`    | `@PreUpdate` de `RemplisseurEtablissement`                            | C8                                           |
| `prepersist`   | refus `@PrePersist` (sans contexte, inter-établissement)              | C5, C10                                      |
| `garde`        | garde AOP `GardeContexteEtablissement`                                | C6                                           |
| `remplissage`  | remplissage automatique d'`etablissement_id`, pour cette entité seule | C9 (et tout cas qui persiste l'entité)       |

C7 ne rougit jamais : il vérifie qu'un `SUPER_ADMIN` ne porte aucune permission
métier, sur un endpoint de `DemoEntite`, indépendamment des fabriques.

C9 rougit par la contrainte `NOT NULL` d'`etablissement_id`, pas par sa propre
assertion : la base détecte le défaut avant le test.

### Si un motif est introuvable

Le script échoue (code 3) plutôt que de produire une ligne verte trompeuse. Le
code gardé a changé : mettre à jour le motif correspondant dans `appliquer()`.
