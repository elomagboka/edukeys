// Wrapper autour de la CLI openapi-typescript : accepte --input <fichier>,
// avec pour valeur par défaut le contrat versionné docs/api/openapi.json.
//
// Ce contrat est committé dans le dépôt, donc régénérer les types ne demande
// ni Docker ni build backend : l'export passe par un @SpringBootTest, qui
// démarre un conteneur PostgreSQL (springdoc introspecte les contrôleurs
// instanciés, donc leurs services, donc les repositories). C'est le contrat
// versionné qui découple les deux. La CI vérifie qu'il n'a pas dérivé du code.
import { spawnSync } from 'node:child_process'

const args = process.argv.slice(2)
const inputFlagIndex = args.indexOf('--input')
const input =
  inputFlagIndex !== -1 && args[inputFlagIndex + 1]
    ? args[inputFlagIndex + 1]
    : '../docs/api/openapi.json'

const result = spawnSync(
  'npx',
  ['openapi-typescript', input, '-o', './src/api/generated/schema.ts'],
  { stdio: 'inherit', shell: true },
)

process.exit(result.status ?? 1)
