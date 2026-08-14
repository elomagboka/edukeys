# ADR-0002 — Multi-établissement

**Statut** : accepté · **Date** : août 2026

## Décision

Une instance de l'application sert plusieurs établissements, isolés
logiquement par une colonne discriminante `etablissement_id`.

---

## 1. Stratégie d'isolation : colonne discriminante

Trois approches existent : une base par établissement, un schéma par
établissement, ou une colonne discriminante dans un schéma partagé.

**Retenu : colonne discriminante.** Une seule base, une seule série de
migrations Flyway, un seul pool de connexions. Les deux autres approches
n'apportent un vrai gain qu'à partir d'exigences réglementaires d'isolation
physique ou de plusieurs centaines d'établissements — et elles transforment
chaque migration en opération de masse.

**Contrepartie assumée** : l'isolation repose entièrement sur le filtrage
applicatif. Une seule requête mal écrite expose les données d'un autre
établissement. D'où la règle suivante, non négociable.

## 2. Le filtrage est automatique, jamais manuel

`etablissement_id` est porté par `BaseEntity`. Un filtre Hibernate
(`@FilterDef` / `@Filter`) est activé sur chaque session à partir de
l'établissement présent dans le contexte de sécurité. Le remplissage à la
persistance est automatique.

Aucun développeur — humain ou agent — n'écrit `where etablissement_id = ?`
à la main. Ce qui est manuel finit par être oublié.

**Trois angles morts du filtre Hibernate, à traiter explicitement :**

- **Requêtes natives** (`@Query(nativeQuery = true)`) : elles échappent au
  filtre. Interdites sur les entités métier. Si l'une devient indispensable
  pour des raisons de performance, le filtrage y est explicite et le test
  d'isolation obligatoire.
- **Traitements asynchrones et batchs** (US-25 clôture de caisse, US-28
  notifications, tâches planifiées) : ils s'exécutent sans contexte de
  sécurité, donc sans établissement. Ils doivent itérer explicitement sur les
  établissements et ouvrir un contexte par établissement.
- **Requêtes d'agrégation transverses** (US-33, US-34) : vérifier qu'elles
  n'agrègent pas au-delà de l'établissement courant.

## 3. Un utilisateur peut appartenir à plusieurs établissements

Modélisé en N-N dès le départ : `utilisateur` ↔ `affectation_etablissement`
(qui porte le rôle) ↔ `etablissement`.

**Pourquoi maintenant, même si la majorité des comptes n'en aura qu'un** : un
enseignant vacataire intervenant dans deux collèges, un parent ayant des
enfants dans deux écoles d'un même groupe, un gestionnaire mutualisé — ces cas
existent dès le premier client réel. Passer de 1-N à N-N après coup impose de
migrer les comptes, les rôles et les jetons en production.

Conséquences :

- **L'email est unique globalement**, pas par établissement. Un utilisateur,
  un compte.
- **Le rôle est porté par l'affectation**, pas par l'utilisateur. La même
  personne peut être Enseignant ici et Gestionnaire là.
- **Le JWT ne porte qu'un seul établissement actif.** Un mécanisme de bascule
  (endpoint de changement d'établissement qui réémet le jeton) couvre le reste.
  Un jeton multi-établissement rendrait le filtrage ambigu.

## 4. Aucune donnée n'est partagée entre établissements

Y compris les référentiels qui semblent universels : niveaux, cycles,
filières, matières. Tous portent un `etablissement_id`.

**Pourquoi** : une exception « données globales » oblige à un filtre
conditionnel, et un filtre conditionnel est un filtre qu'on peut se tromper à
appliquer. La cohérence vaut mieux que l'économie de quelques lignes.

Le confort de saisie est traité autrement : à la création d'un établissement,
un **modèle d'initialisation** copie un référentiel type (niveaux 6ème à
Terminale, cycles, matières courantes). L'établissement le modifie ensuite
librement. C'est aussi ce que suppose le backlog, où US-02 et US-03 confient
la création des niveaux et matières à l'Administrateur.

## 5. Un rôle au-dessus des établissements

`SUPER_ADMIN` : crée les établissements, gère les comptes d'administrateurs,
n'a **aucun accès aux données métier** (élèves, notes, finances). Son
périmètre s'arrête à l'administration de la plateforme.

Ce rôle s'ajoute aux six du backlog, qui sont tous des rôles *internes* à un
établissement. C'est lui qui exécute US-00.

Le filtre Hibernate est désactivé pour ce rôle uniquement, sur un ensemble
restreint d'entités (établissement, utilisateur), jamais sur les données
métier.

## 6. Unicité du matricule (US-08)

Unique **par établissement et par année scolaire**, avec un préfixe
d'établissement le rendant unique globalement de fait.

Format : `<CODE_ETAB>-<ANNEE>-<SEQUENCE>`, par exemple `CSJ-2026-00147`.
La séquence est gérée en base (pas en mémoire applicative) pour résister à la
concurrence — deux inscriptions simultanées ne doivent pas produire le même
matricule.

---

## Impacts sur le Sprint 0

- **T-04** : ajouter `SUPER_ADMIN`, modéliser `affectation_etablissement`,
  inclure l'établissement actif dans le JWT, prévoir l'endpoint de bascule.
- **T-05** : devient obligatoire, et son test d'isolation est le test le plus
  important du sprint.
- **T-08** : les fabriques de test créent systématiquement **deux**
  établissements. Tout test d'une entité métier vérifie l'isolation.
- **T-10** (US-00) : inclut le modèle d'initialisation du référentiel.

## Risque principal et sa parade

Le risque n'est pas technique, il est humain : quelqu'un finira par écrire une
requête qui contourne le filtre. La parade est un test d'isolation générique,
exécuté en CI, qui parcourt toutes les entités métier et vérifie qu'un
utilisateur de l'établissement A ne peut atteindre aucune donnée de
l'établissement B — y compris par accès direct à l'identifiant.

Ce test s'écrit une fois en T-05 et protège les 35 US suivantes.
