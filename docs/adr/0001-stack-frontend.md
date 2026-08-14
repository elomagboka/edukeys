# ADR-0001 — Stack frontend

**Statut** : accepté · **Date** : août 2026

## Décision

React 19 + **TypeScript**, build Vite, application monopage consommant l'API
REST du backend Spring Boot.

## React 19

Stable depuis décembre 2024, mûr et en support actif. React 18 ne reçoit plus
que les correctifs de sécurité critiques : démarrer un projet neuf dessus
créerait une dette de migration sans contrepartie.

Apports concrets ici : le compilateur React supprime l'essentiel des `useMemo`
et `useCallback` placés à la main, et `useActionState` simplifie les
formulaires — utile sur un backlog qui en compte plusieurs dizaines.

## TypeScript

Choix structurant, motivé par trois raisons propres à ce projet :

1. **Contrat vérifié de bout en bout.** Le backend expose son OpenAPI via
   SpringDoc. `openapi-typescript` en dérive de vrais types. Conséquence : si
   une US backend renomme un champ d'un DTO, le build frontend **casse à la
   compilation**, en CI, avant la mise en production. Sans typage, la même
   erreur se manifeste par un `undefined` affiché à l'écran, découvert par un
   utilisateur.

2. **Développement assisté par IA.** Sans types, un agent qui écrit un
   composant consommant un DTO de bulletin (notes, coefficients, moyennes
   imbriqués) n'a aucun moyen de vérifier qu'il n'invente pas un champ. Le
   compilateur est le garde-fou qui rend la délégation fiable.

3. **Durée du projet.** 36 US, 20 semaines, une dizaine de modules. Le coût du
   typage est payé d'avance et constant ; le coût de son absence croît avec la
   taille de la base de code.

## Règles de typage

1. **`strict: true`** dans `tsconfig.json`. Non négociable — sans strict, TS
   ne rapporte qu'une fraction de son intérêt.
2. **Aucun type de DTO écrit à la main.** Tous les types de données serveur
   viennent de `src/api/generated/`. Recopier une interface de DTO recrée
   exactement le problème que le typage devait résoudre : deux définitions qui
   divergent en silence.
3. **`any` interdit** (règle ESLint bloquante). Si un type est réellement
   inconnu, `unknown` + rétrécissement explicite.
4. **`as` réservé aux cas justifiés**, avec un commentaire expliquant pourquoi
   le compilateur ne peut pas déduire. Une assertion est une promesse non
   vérifiée.
5. **Types de formulaires dérivés des schémas Zod** via `z.infer`. Une seule
   source de vérité par formulaire, jamais un schéma plus une interface.

## Bibliothèques retenues

| Besoin | Choix | Raison |
| :--- | :--- | :--- |
| Build | Vite | Standard, démarrage instantané |
| Routage | React Router | Routes protégées par rôle |
| État serveur | TanStack Query | Cache, invalidation, états de chargement ; excellente inférence de types |
| État client | Zustand | Session et préférences UI uniquement |
| Formulaires | React Hook Form + Zod (`zodResolver`) | Validation runtime + types dérivés |
| Client HTTP | `openapi-fetch` | Typé directement depuis le contrat OpenAPI |
| Composants | Ant Design | Écrit en TS, tables denses, formulaires, locale FR complète |
| Calendrier | FullCalendar | Emplois du temps (US-14) |
| Graphiques | Recharts | Tableaux de bord (US-26, US-33, US-34) |
| Dates | Day.js | Imposé par Ant Design |
| Tests | Vitest + Testing Library + MSW | MSW simule l'API sans backend démarré |
| i18n | react-i18next | Français par défaut |

**Pourquoi Ant Design** : ce n'est pas une vitrine mais un outil de production
utilisé quotidiennement par des secrétaires, des enseignants et des
comptables. Ses points forts recouvrent exactement les besoins du backlog —
tableaux denses, formulaires longs, sélecteurs de dates, upload de fichiers.
Le portail parent/élève (US-27 à US-32) pourra recevoir un thème distinct :
public différent, usage occasionnel, souvent mobile.

## Structure

`frontend/src/features/` reflète **exactement** les modules du backend
(`etablissement`, `academique`, `identite`, `admission`, `eleve`, `pedagogie`,
`finance`, `portail`, `reporting`). Cette symétrie permet de travailler une US
de bout en bout sans charger le reste du projet dans le contexte.

## Conséquences

- Le contrat OpenAPI devient un livrable de première classe. Toute US backend
  modifiant un DTO impose `npm run api:generate`. La CI vérifie que les types
  générés sont à jour et échoue sinon.
- `tsc --noEmit` est une étape bloquante de la CI, distincte du build.
- Pas de rendu serveur ni de Server Components : SPA derrière authentification,
  le SEO n'a aucun intérêt ici.
- Le déploiement sert des fichiers statiques ; l'API est sur une autre origine
  (prévoir CORS côté Spring Security dès T-04).
