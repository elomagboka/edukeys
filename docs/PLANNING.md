# Planning — Edukeys

**36 user stories · 13 itérations · environ 25 semaines**

Ce planning remplace celui du backlog, qui présentait trois anomalies :
US-00 n'apparaissait dans aucun sprint, US-06 (formulaire public) précédait
US-04 (modèle de permissions), et le récapitulatif annonçait 25 stories
« Must Have » pour 28 identifiants listés.

---

## Vue d'ensemble

| # | Sprint | Durée | Contenu |
| :--- | :--- | :--- | :--- |
| 0 | Socle technique | 1 sem | T-01 à T-11 + **US-00** |
| 1 | Référentiel académique | 2 sem | US-04, 01, 02, 03, 05 |
| 2 | Admission | 2 sem | US-06, 07 |
| 3 | Dossier élève | 2 sem | US-08, 09, 10 |
| 4 | Vie scolaire | 2 sem | US-11, 12, 13 |
| EDT | Emplois du temps | 2 sem | **US-14** (sprint dédié) |
| 5 | Présences et devoirs | 2 sem | US-15, 16, 17 |
| 6 | Évaluation | 2 sem | US-18, 19, 20 |
| 7 | Facturation | 2 sem | US-21, 22, 23 |
| 8 | Trésorerie | 2 sem | US-24, 25, 26 |
| 9 | Portails | 2 sem | US-27, 30, 31 |
| 10 | Communication | 2 sem | US-28a, 28b, 28d |
| 11 | Pilotage | 2 sem | US-29, 33, 34, 35 |
| — | *Phase mobile* | *à définir* | *App mobile + US-28c (push)* |

---

## Détail et raisons d'ordonnancement

### Sprint 0 — Socle technique (1 semaine)

Voir `docs/SPRINT-0.md`. Se termine par **US-00** (établissements et sites),
qui sert de module de référence pour tout le reste.

### Sprint 1 — Référentiel académique

US-04 (RBAC) · US-01 (années) · US-02 (structure) · US-03 (matières) ·
US-05 (périodes)

**US-04 vient en tête, et c'est la correction principale du planning.** Le
socle de sécurité est déjà posé en T-04, mais la gestion complète des
utilisateurs et des rôles doit être terminée avant toute fonctionnalité
exposée. Le backlog plaçait US-06 — un formulaire de pré-inscription
**public** — avant US-04. C'était ouvrir une porte sans avoir décidé qui a le
droit d'entrer.

Les quatre autres US du sprint sont du paramétrage sans dépendance entre elles :
elles peuvent être menées dans n'importe quel ordre, et se prêtent bien à la
répétition d'un même patron de code.

### Sprint 2 — Admission

US-06 (pré-inscription en ligne) · US-07 (traitement des dossiers)

US-06 est le premier endpoint accessible sans authentification. Prévoir dès
maintenant la limitation de débit et la protection anti-robot : un formulaire
public non protégé sera rempli automatiquement dans la semaine qui suit sa
mise en ligne.

### Sprint 3 — Dossier élève

US-08 (matricule) · US-09 (responsables légaux) · US-10 (dossier)

US-08 dépend d'US-00 pour le code d'établissement (format `CSJ-2026-00147`,
ADR-0002) et de la séquence en base pour résister à la concurrence.

### Sprint 4 — Vie scolaire

US-11 (mouvements) · US-12 (recherche) · US-13 (affectations enseignants)

US-13 est placée ici parce qu'elle **conditionne entièrement le sprint EDT**.
Sans affectation enseignant/matière/classe, il n'y a rien à placer dans un
emploi du temps.

US-12 exploite la recherche paginée de T-03 ; c'est le moment de vérifier que
le socle tient sur un cas réel.

### Sprint EDT — Emplois du temps (2 semaines)

US-14. Voir `docs/SPRINT-EDT.md`.

Sprint isolé parce qu'il change de nature : c'est un problème de contraintes,
pas un CRUD. La contrainte de trajet inter-sites (ADR-0005) en fait partie
intégrante.

### Sprint 5 — Présences et devoirs

US-15 (appel numérique) · US-16 (devoirs) · US-17 (soumission en ligne)

US-15 vient **après** le sprint EDT : l'appel a besoin des séances pour savoir
qui est censé être présent, quand et où.

C'est aussi la fonctionnalité la plus sensible au réseau — elle se fait en
classe, souvent sur téléphone. Prévoir une tolérance aux coupures (ADR-0003).

### Sprint 6 — Évaluation

US-18 (saisie des notes) · US-19 (validation) · US-20 (bulletins)

Ordre imposé : on ne publie pas un bulletin construit sur des notes non
validées. US-20 est la plus lourde des trois — génération PDF, calculs de
moyennes pondérées, rangs.

### Sprint 7 — Facturation

US-21 (frais) · US-22 (paiements) · US-23 (solde parent)

US-21 avant US-22, évidemment : pas de paiement sans grille tarifaire.

Règle à tenir dans tout ce sprint : **aucun montant en virgule flottante**.
`BigDecimal` côté Java, `numeric` en base, et le calcul reste côté serveur
(ADR-0003).

### Sprint 8 — Trésorerie

US-24 (dépenses) · US-25 (clôture de caisse) · US-26 (tableau de bord)

La caisse est rattachée à un site (ADR-0005), la consolidation se fait au
niveau établissement.

US-25 comporte un traitement planifié : il s'exécute sans contexte de
sécurité et doit donc itérer explicitement sur les établissements (ADR-0002).

### Sprint 9 — Portails

US-27 (parent) · US-30 (élève) · US-31 (enseignant)

Regroupés parce qu'ils partagent la même mécanique : filtrage par propriété
des données. Un parent ne voit que ses enfants, un élève que ses notes. Ce
n'est pas du RBAC — c'est un filtrage par appartenance, conçu en T-04 et
appliqué ici.

Ces trois portails consomment des données déjà produites : rien de nouveau
côté modèle, beaucoup côté interface.

### Sprint 10 — Communication

US-28a (moteur + in-app) · US-28b (email + préférences) · US-28d (SMS)

Voir ADR-0006. L'abstraction est posée dès le Sprint 0 ; ce sprint livre le
moteur et les trois canaux qui ne dépendent d'aucune application mobile.

Le SMS est ici et non plus tard, parce qu'il ne dépend de rien et qu'il touche
la part des parents qui n'auront jamais de smartphone. Il apporte en revanche
trois exigences propres, à traiter dans l'US et non après : gabarits sans
accents (contrainte GSM-7), normalisation E.164 des numéros à la saisie, et
plafond de dépense par établissement.

Le canal push (US-28c) **n'est pas ici** : sans application mobile pour
recevoir les notifications, il serait impossible à tester.

Sprint dense. Si le temps manque, US-28b (email) peut glisser d'un sprint —
jamais le plafond de dépense du SMS.

### Sprint 11 — Pilotage

US-29 (messagerie) · US-33 (tableau de bord direction) · US-34 (classes à
risque) · US-35 (rapports)

US-32 (rendez-vous en ligne) sort du planning ferme : seule story « Could
Have » du backlog, elle se greffe ici si le sprint le permet.

Le reporting arrive en dernier parce qu'il n'a de sens qu'avec des données
réelles.

Attention aux agrégations transverses : elles doivent rester dans le périmètre
de l'établissement courant (ADR-0002).

### Phase mobile — après la V1

Application mobile + US-28c (push) + enregistrement des appareils.

Elle réutilise la même API REST, d'où l'importance du contrat OpenAPI. Deux
points à ne pas découvrir à ce moment-là : le rafraîchissement de jeton doit
tenir sur une session longue, et les charges utiles du portail parent doivent
rester compactes.

---

## Chemin critique

```
US-00 → US-04 → US-05 → US-13 → US-14 → US-15 → US-18 → US-20
```

Tout retard sur cette chaîne décale l'ensemble. Les sprints finances (7 et 8)
et portails (9) en sont indépendants et peuvent être réordonnés selon les
priorités du premier établissement client.

## Ce qui peut sauter si le temps manque

Dans cet ordre : US-32 (rendez-vous), US-34 (classes à risque), US-29
(messagerie), US-17 (soumission en ligne des devoirs), US-24 (dépenses).

Aucune n'est sur le chemin critique, et une école fonctionne sans elles.

## Ce qui ne peut jamais sauter

US-00, US-04, US-05, US-08, US-13, US-14, US-15, US-18, US-20, US-22. Sans
l'une d'elles, le produit ne remplit pas sa fonction de base : inscrire des
élèves, les évaluer, encaisser des frais.
