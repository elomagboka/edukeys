# ADR-0008 — Marché visé et conception régionale

**Statut** : accepté · **Date** : août 2026

## Décision

Edukeys est conçu pour les **pays francophones d'Afrique de l'Ouest et
centrale**, pas pour le seul Togo. Cette ambition ne se traduit par aucune
abstraction supplémentaire aujourd'hui, mais elle interdit certains raccourcis.

---

## Pourquoi cette zone se comporte comme un marché unique

L'héritage éducatif commun rend le produit transposable sans refonte :

- **Structure scolaire identique** : sixième à terminale, cycles primaire,
  collège et lycée, séries au lycée
- **Mêmes diplômes** : BEPC, baccalauréat
- **Notation sur 20**, moyennes pondérées par coefficients
- **Découpage en trois trimestres**
- **Zone franc CFA** : XOF pour l'UEMOA, XAF pour la CEMAC — deux codes, même
  parité fixe, même absence de décimales, aucune conversion
- **Fuseaux UTC+0 ou UTC+1**, une heure d'écart au maximum

Le backlog produit décrit donc déjà, sans le savoir, un système valable dans
quatorze pays.

## Ce qui varie, et comment on le traite

**Par établissement, jamais par pays.** C'est la règle centrale de cet ADR.

Deux écoles du même quartier n'ont pas le même barème de passage, ni le même
nombre de devoirs par trimestre, ni le même format de bulletin. La variabilité
inter-établissements est déjà plus forte que la variabilité inter-pays. Rendre
ces paramètres configurables au niveau de l'établissement (ADR-0002 : aucun
référentiel partagé) couvre donc automatiquement les différences nationales.

**Conséquence pratique** : aucun `if (pays == "TG")` dans le code métier.
Jamais. Si un comportement doit différer, c'est un paramètre d'établissement.

**Trois champs sur `Etablissement`** : `pays` (code ISO), `fuseauHoraire`,
`devise`. Stockés dès T-10 — trois colonnes ajoutées maintenant coûtent moins
qu'une migration sur une table peuplée.

État actuel de leur exploitation :

| Champ | Aujourd'hui |
| :--- | :--- |
| `pays` | Stocké, affiché sur les documents édités |
| `fuseauHoraire` | Stocké, **non appliqué à l'affichage** — voir issue dédiée |
| `devise` | Stocké, affiché ; aucune conversion (parité fixe) |

Le fuseau mérite une vigilance particulière : les horodatages sont en
`TIMESTAMPTZ`, donc absolus et corrects en base. Seul l'affichage est
aujourd'hui en UTC. Un établissement béninois verra donc une heure de moins
que la réalité locale tant que la conversion n'est pas faite côté frontend.
C'est un défaut d'affichage, pas de données.

## Ce que cette ambition impose dès maintenant

**Les paiements passeront par une abstraction.** Le mobile money domine toute
la zone, mais les opérateurs diffèrent : Wave et Orange Money au Sénégal, MTN
et Moov dans plusieurs pays, Flooz au Togo. L'US-22 exposera donc une
interface `MoyenPaiement` avec une implémentation par opérateur — même schéma
que le `Notificateur` d'ADR-0006.

À construire au moment de l'US-22, pas avant. Mais à ne pas coder en dur sur
un seul opérateur.

**Le SMS restera un canal de premier plan.** Ce qui vaut au Togo vaut partout
dans la zone : le taux d'équipement en téléphone dépasse largement celui en
smartphone. Confirme la décision d'ADR-0006 de livrer le SMS en V1.

**Aucune donnée de référence codée en dur.** Ni liste de matières, ni barème,
ni format de matricule. Tout passe par le référentiel d'établissement,
initialisé depuis un modèle (T-10, SPI `InitialisateurReferentiel`).

## Ce que cette ambition n'impose pas

Aucune bibliothèque de fuseaux horaires, aucun moteur de conversion monétaire,
aucun système éducatif paramétrable. Construire ces abstractions maintenant
reviendrait à deviner des besoins inconnus — et produirait probablement les
mauvaises.

Le principe : **rendre configurable ce qui varie déjà entre deux écoles
voisines. Ne rien anticiper au-delà.**

## Le vrai coût de l'expansion

Il n'est pas technique.

**Le juridique.** Chaque pays a son cadre de protection des données
personnelles et son autorité de contrôle. Les données traitées sont celles de
mineurs — identité, santé, situation familiale, finances des parents. Aucune
préparation dans le code ne rendra cette conformité plus simple : elle se
traite pays par pays, avant d'y entrer.

**La distribution.** Vendre à des établissements dans un pays où l'on n'a
aucune présence est un problème commercial, pas d'architecture.

**Le support.** Assistance téléphonique sur plusieurs fuseaux, dans des pays
où l'on ne connaît pas les usages administratifs locaux.

Ces trois points décideront de l'expansion bien avant le code.

## Hors périmètre

Les pays anglophones voisins — Ghana, Nigéria — ont un système éducatif
entièrement différent : structure scolaire, notation, diplômes, monnaie. Y
entrer ne serait pas une adaptation mais un second produit. Explicitement hors
de cet ADR.

## Conséquences

- Trois champs ajoutés à `Etablissement` en T-10
- Une issue ouverte pour la conversion du fuseau à l'affichage (T-11)
- L'US-22 conçue avec une abstraction des moyens de paiement
- Aucun test de pays dans le code métier — règle vérifiable en revue
