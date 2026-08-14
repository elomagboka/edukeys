# ADR-0005 — Sites et annexes

**Statut** : accepté · **Date** : août 2026

## Le besoin

Un établissement peut être réparti sur plusieurs implantations physiques :
un campus primaire d'un côté, un collège de l'autre, parfois à plusieurs
kilomètres. Ce n'est pas la même chose que plusieurs établissements clients
(ADR-0002) : il s'agit ici d'**un seul client**, avec une direction unique,
un référentiel commun et une comptabilité consolidée.

## Décision

Un second niveau hiérarchique, `Site`, à l'intérieur de l'établissement.

```
Établissement (cloisonnement de sécurité — ADR-0002)
   └── Site (organisation interne)
         ├── Classes
         ├── Salles
         └── Inscriptions d'élèves
```

**Tout établissement possède au moins un site**, créé automatiquement à sa
création et nommé d'après lui. Un établissement mono-implantation a donc un
site unique dont l'utilisateur n'entend jamais parler : l'interface ne
propose de sélecteur de site que lorsqu'il en existe plusieurs.

C'est la règle de conception centrale : **le cas simple ne paie pas la
complexité du cas complexe.**

---

## `site_id` n'est pas un second `etablissement_id`

Distinction essentielle, source de confusion garantie si elle n'est pas
posée d'emblée :

| | `etablissement_id` | `site_id` |
| :--- | :--- | :--- |
| Nature | Cloisonnement de **sécurité** | Organisation **métier** |
| Portée | Toute entité métier, sans exception | Seulement les entités localisées |
| Filtrage | Automatique, via filtre Hibernate | Explicite, dans les services |
| Franchissable | Jamais | Oui — la direction voit tous ses sites |

**Le site n'entre pas dans le filtre Hibernate.** Si c'était le cas, un
directeur ne pourrait plus consulter l'ensemble de ses annexes, ce qui est
précisément ce qu'on lui demande de faire.

## Quelles entités portent un site

**Oui** — elles ont une existence physique ou une appartenance locale :
`Classe`, `Salle`, `Inscription` (donc l'élève pour une année donnée),
`Seance` d'emploi du temps, `Caisse` et opérations de caisse.

**Non** — elles appartiennent à l'établissement entier : `Niveau`, `Cycle`,
`Filiere`, `Matiere`, `AnneeScolaire`, `Periode`, `Utilisateur`, `Eleve`
(l'identité de l'élève, distincte de son inscription).

## Conséquences par domaine

### Élèves et inscriptions

L'élève appartient à l'établissement ; son **inscription** appartient à un
site. Un élève qui passe du primaire au collège d'un même établissement change
de site sans changer d'identité ni de matricule.

**Le matricule reste unique au niveau établissement**, jamais au niveau site
(ADR-0002). Sinon, un passage d'annexe le renumérerait, et l'historique
scolaire se casserait.

### Personnel

Un enseignant peut intervenir sur plusieurs sites. Son affectation
(ADR-0002) porte donc un **périmètre de sites** :

- périmètre vide = tous les sites de l'établissement (cas de la direction)
- périmètre renseigné = restreint à ces sites

Pas de nouveau rôle « responsable d'annexe » : c'est un rôle existant
(Direction ou Gestionnaire) dont le périmètre est limité à un site. Ajouter
un rôle dupliquerait la matrice de permissions sans rien apporter.

### Emplois du temps — le point critique

C'est là que les sites coûtent le plus cher, et cela concerne directement
l'US-14.

1. **Une salle appartient à un site.** Affecter une classe du site A à une
   salle du site B doit être impossible, pas seulement déconseillé.
2. **Contrainte de trajet inter-sites.** Un enseignant en cours à 8h sur le
   site A ne peut pas enseigner à 9h sur le site B distant de 15 km. Il faut
   donc une **matrice de temps de trajet** entre sites, et un contrôle de
   conflit qui en tient compte. Un enseignant multi-sites n'est jamais un
   simple problème de disponibilité horaire.
3. Les créneaux horaires peuvent différer d'un site à l'autre : un primaire
   et un secondaire n'ont pas les mêmes journées.

### Finances

La caisse est tenue **par site** — c'est la réalité opérationnelle, chaque
implantation encaisse chez elle. La consolidation comptable et les tableaux
de bord (US-26, US-33) se font au niveau établissement, avec possibilité de
ventiler par site.

### Emplois du temps et bulletins

Les documents édités (bulletins, certificats, reçus) portent l'en-tête de
l'établissement, et mentionnent le site quand il y en a plusieurs.

---

## Impacts sur le Sprint 0 et le backlog

- **T-10 (US-00)** : la création d'un établissement crée automatiquement son
  site principal. L'entité `Site` fait partie de cette US.
- **US-05 (classes)** : une classe est rattachée à un site.
- **US-08 (matricule)** : inchangé — la séquence reste au niveau établissement.
- **US-14 (emplois du temps)** : impact majeur, voir `docs/SPRINT-EDT.md`.
- **US-21 à US-25 (finances)** : la caisse est rattachée à un site.

## Ce qu'on ne fait pas

Pas de hiérarchie à trois niveaux (établissement → site → bâtiment). Si le
besoin de localiser finement une salle apparaît, un champ texte « bâtiment »
sur la salle suffira. Un troisième niveau relationnel complexifierait chaque
requête pour un gain nul.

Pas de site pour les entités du référentiel. Si une annexe voulait un jour
ses propres matières, ce serait le signe qu'il s'agit en réalité de deux
établissements distincts.
