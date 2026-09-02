import type { DataTableColonne } from './DataTable'

/**
 * Verrouille la correction du typage `dataIndex` (frontend/CLAUDE.md, contrat
 * OpenAPI réellement exercé) : ce fichier ne s'exécute jamais, il n'existe
 * que pour `tsc -b --noEmit` (npm run typecheck, bloquant en CI). Si
 * `DataTableColonne` est un jour « simplifié » pour reprendre le `dataIndex`
 * large d'AntD (`string | number`), la ligne ci-dessous cesse d'être en
 * erreur et `@ts-expect-error` devient lui-même une erreur TS2578 —
 * le build casse plutôt que de laisser la régression passer en silence.
 */
interface Exemple {
  id: string
  nom: string
}

// @ts-expect-error 'champInexistant' n'est pas une clé de `Exemple`
const colonneInvalide: DataTableColonne<Exemple> = { dataIndex: 'champInexistant', key: 'x' }
void colonneInvalide

const colonneValide: DataTableColonne<Exemple> = { dataIndex: 'nom', key: 'nom' }
void colonneValide
