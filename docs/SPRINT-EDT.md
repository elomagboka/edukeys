# SPRINT EDT — Emplois du temps (US-14)

**Durée** : 2 semaines (un sprint entier, plus une marge) · **Prérequis** :
US-05 (classes), US-13 (affectations enseignants), et ADR-0005 (sites).

---

## Pourquoi une US mérite un sprint entier

Toutes les autres US du backlog sont des variations autour de la même
mécanique : un formulaire, une validation, une persistance, une liste. L'US-14
n'est pas de cette famille. « Détection automatique des conflits » sur un
emploi du temps est un **problème de satisfaction de contraintes**, où chaque
placement en contraint d'autres, et où le nombre de combinaisons explose.

Concrètement, un collège de 12 classes, 25 enseignants, 15 salles et 30
créneaux hebdomadaires représente plusieurs centaines de séances à placer,
chacune devant satisfaire simultanément une dizaine de contraintes. Traité
comme un CRUD, on obtient un outil qui laisse créer des emplois du temps
impossibles, découverts par les enseignants le jour de la rentrée.

---

## Décision de périmètre : assistance, pas génération

**En V1, Edukeys n'engendre pas automatiquement les emplois du temps.** Il
offre une saisie assistée avec détection de conflits en temps réel.

La génération automatique complète est un solveur de contraintes. Les
logiciels spécialisés du marché y ont consacré des années. Tenter de la
reproduire ici consommerait le budget de plusieurs sprints pour un résultat
que les établissements rejetteraient de toute façon — un emploi du temps
engendré sans intervention humaine ignore les arrangements informels qui font
qu'une école fonctionne.

Ce que la V1 fait, et qui couvre l'essentiel du besoin :

- proposer les créneaux réellement disponibles au moment du placement
- refuser tout placement produisant un conflit dur
- signaler les préférences non respectées sans bloquer
- détecter les trous, surcharges et déséquilibres
- dupliquer un emploi du temps d'une année sur l'autre

La génération assistée par lot pourra faire l'objet d'une évolution ultérieure,
une fois les contraintes réelles observées en production.

---

## Modèle de contraintes

C'est le cœur du sprint. La distinction entre contrainte **dure** et
**souple** structure tout le reste.

### Contraintes dures — un placement les violant est refusé

| Contrainte | Règle |
| :--- | :--- |
| Classe | Une classe suit une seule séance à un instant donné |
| Enseignant | Un enseignant assure une seule séance à un instant donné |
| Salle | Une salle accueille une seule séance à un instant donné |
| Site | La salle doit appartenir au site de la classe |
| Trajet | Deux séances d'un même enseignant sur des sites différents doivent être séparées d'au moins le temps de trajet |
| Capacité | L'effectif de la classe ne dépasse pas la capacité de la salle |
| Indisponibilité | Aucune séance sur une indisponibilité déclarée |
| Volume horaire | Le volume placé ne dépasse pas le volume prévu pour la matière |

### Contraintes souples — signalées, jamais bloquantes

Journée trop chargée, trous dans l'emploi du temps d'une classe, matières
difficiles placées en fin de journée, non-respect des préférences d'un
enseignant, déséquilibre d'une matière sur la semaine.

Elles produisent un **score de qualité** affiché à l'utilisateur, qui décide.

> La contrainte de trajet inter-sites est la plus souvent oubliée et la plus
> visible en production : elle produit des emplois du temps que personne ne
> peut respecter physiquement. Elle découle directement d'ADR-0005.

---

## Découpage en tâches

### E-01 — Modèle et référentiel (2 j)

`GrilleHoraire` (créneaux d'un site — un primaire et un collège n'ont pas la
même journée), `Salle` (rattachée à un site, avec capacité et type),
`Indisponibilite` (enseignant, salle ou classe, ponctuelle ou récurrente),
`MatriceTrajet` (temps de trajet entre sites), `Seance`.

**Fin** : le référentiel se saisit, aucune logique de placement encore.

### E-02 — Moteur de contraintes (3 j)

Un service isolé, sans dépendance à la couche web, testable seul.

```
ResultatValidation valider(Seance candidate, ContexteEDT contexte)
  → conflits durs (bloquants) + avertissements souples + score
```

**Chaque contrainte est une classe distincte** implémentant une interface
commune, enregistrée dans une liste. Ajouter une contrainte devient alors
l'ajout d'une classe et d'un test, sans toucher au moteur.

**Fin** : chaque contrainte dure a son test dédié, cas positif et négatif,
y compris la contrainte de trajet inter-sites.

### E-03 — Recherche de créneaux disponibles (2 j)

L'inverse de la validation : au lieu de valider un placement proposé,
énumérer les placements possibles.

```
List<Creneau> creneauxPossibles(Classe, Matiere, Enseignant, Semaine)
```

Attention aux performances : cette recherche est appelée à chaque interaction
de l'utilisateur. Prévoir un chargement du contexte de la semaine en une
requête, puis un travail en mémoire. **Aucune requête dans la boucle de
recherche** — c'est ici que se joue la fluidité perçue.

**Fin** : une recherche répond en moins de 200 ms sur un jeu réaliste
(12 classes, 25 enseignants, une semaine complète).

### E-04 — API (1,5 j)

Consultation par classe, enseignant, salle ou site. Placement, déplacement,
suppression de séance. Validation d'un emploi du temps complet. Duplication
d'une semaine ou d'une année. Export.

Le placement renvoie systématiquement les conflits et avertissements, même
en cas de succès — le frontend en a besoin pour afficher le score.

### E-05 — Interface de construction (3 j)

L'écran le plus complexe du produit.

- Grille hebdomadaire, une colonne par jour, glisser-déposer des séances
- **Au moment de la saisie, les créneaux impossibles sont visuellement
  désactivés** — empêcher l'erreur vaut mieux que la signaler après coup
- Retour immédiat sur les conflits, avec la raison exprimée en clair
  (« M. Kossi enseigne au site Tokoin à 8h, 20 minutes de trajet »)
- Panneau latéral : matières restant à placer et volume horaire restant
- Bascule entre les vues classe, enseignant et salle

Contrainte de latence (ADR-0003) : **la validation d'un déplacement ne doit
pas nécessiter d'aller-retour serveur.** Charger le contexte de la semaine à
l'ouverture, valider côté client pendant la manipulation, confirmer côté
serveur à la dépose. Sinon, chaque glisser-déposer coûte 100 ms d'attente et
l'écran devient inutilisable.

Le serveur reste l'autorité : la validation client est de l'ergonomie, pas de
la sécurité.

### E-06 — Consultation et diffusion (1,5 j)

Vues en lecture seule pour enseignants, élèves et parents (US-29, US-31).
Export PDF par classe et par enseignant. Signalement des modifications
postérieures à la publication.

### E-07 — Tests sur données réelles (1 j)

Reprendre l'emploi du temps papier d'un établissement pilote et vérifier que
le système le reproduit sans le refuser. **Si le moteur rejette un emploi du
temps qui fonctionne dans la réalité, ce sont les contraintes qui sont
fausses, pas l'établissement.**

C'est la tâche la plus instructive du sprint. Ne la sacrifie pas si le
planning glisse.

---

## Position dans le planning

Ce sprint intervient **après** US-13 (affectations enseignant/matière/classe),
dont il dépend entièrement, et **avant** US-15 (appel numérique), qui a besoin
des séances pour savoir qui est censé être présent.

## Risques

**Les contraintes réelles diffèrent des contraintes imaginées.** D'où E-07,
placé dans le sprint et non après. Chaque établissement a ses règles propres
(le mercredi après-midi, les cours de sport groupés, la salle informatique
partagée).

**La tentation de la génération automatique.** Si un utilisateur la réclame en
cours de sprint, note la demande et poursuis. La saisie assistée doit
fonctionner parfaitement avant qu'on envisage d'automatiser quoi que ce soit.

**Le glisser-déposer est chronophage.** Si E-05 déborde, livre d'abord une
saisie par formulaire avec la même validation : moins agréable, tout aussi
correcte. L'ergonomie s'améliore ensuite, la justesse non.
