# Frontend — Contexte

Complète le `CLAUDE.md` racine. S'applique à tout ce qui est sous `frontend/`.

## Stack

React 19 + **TypeScript strict**, Vite, React Router, TanStack Query, Zustand,
React Hook Form + Zod, `openapi-fetch`, Ant Design, Day.js,
Vitest + Testing Library + MSW.

## Structure

```
frontend/src/
  api/
    generated/     # types issus de l'OpenAPI — NE JAMAIS ÉDITER À LA MAIN
    client.ts      # openapi-fetch : baseURL, JWT, refresh, erreurs RFC 7807
  features/
    <module>/      # miroir exact des modules backend
      api.ts       # hooks TanStack Query de ce module
      schemas.ts   # schémas Zod des formulaires
      components/
      pages/
  shared/
    components/    # DataTable, PageHeader, ConfirmDialog, FormModal
    hooks/
    types/         # types transverses UNIQUEMENT (jamais de DTO serveur)
  routes/          # routes + gardes par rôle
  stores/          # Zustand : session, préférences UI. Rien d'autre.
```

## Règles

1. **Aucune donnée serveur dans Zustand.** Tout ce qui vient de l'API passe par
   TanStack Query. C'est la règle la plus violée sur ce type de projet et la
   plus coûteuse à réparer.
2. **Aucun type de DTO écrit à la main.** Ils viennent de `api/generated/`.
   Recopier une interface de DTO annule l'intérêt du typage.
3. **`any` interdit.** `unknown` + rétrécissement si le type est réellement
   inconnu. Règle ESLint bloquante.
4. **Aucun `fetch` direct dans un composant.** Toujours un hook du `api.ts` de
   la feature.
5. **Aucun import croisé entre features.** Le partagé va dans `shared/`.
6. **Tout formulaire** : React Hook Form + `zodResolver`, types via `z.infer`.
   Pas de `useState` par champ.
7. **Aucune chaîne visible en dur.** Fichiers de traduction, même en français
   seul.
8. **Permissions à deux niveaux** : garde de route par rôle, et masquage des
   actions interdites. Le backend reste la seule autorité ; le frontend ne
   fait que de l'ergonomie.
9. **Trois états obligatoires** sur toute vue de données : chargement, erreur,
   vide. Une vue qui n'a que le cas nominal est incomplète.

## Commandes

```bash
npm run dev
npm run api:generate   # régénère les types depuis l'OpenAPI du backend
npm run typecheck      # tsc --noEmit — bloquant en CI
npm run lint           # bloquant en CI
npm test
npm run build
```

## Avant de coder une feature

Vérifie que `api/generated/` est à jour. Si l'US backend correspondante vient
d'être livrée, lance `npm run api:generate` d'abord. Ne devine jamais la forme
d'un DTO : lis le type généré.
