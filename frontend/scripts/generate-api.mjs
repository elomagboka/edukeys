// Wrapper autour de la CLI openapi-typescript : accepte --input <fichier>
// (utilisé par la CI, qui télécharge le contrat backend en artefact et le
// pointe explicitement) avec une valeur par défaut pour l'usage local, où
// le build backend a déjà écrit backend/target/openapi.json.
import { spawnSync } from 'node:child_process'

const args = process.argv.slice(2)
const inputFlagIndex = args.indexOf('--input')
const input =
  inputFlagIndex !== -1 && args[inputFlagIndex + 1]
    ? args[inputFlagIndex + 1]
    : '../backend/target/openapi.json'

const result = spawnSync(
  'npx',
  ['openapi-typescript', input, '-o', './src/api/generated/schema.ts'],
  { stdio: 'inherit', shell: true },
)

process.exit(result.status ?? 1)
