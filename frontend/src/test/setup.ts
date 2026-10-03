import '@testing-library/jest-dom/vitest'
import { configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './mswServer'

// jsdom n'implémente pas matchMedia : AntD (Grid/breakpoints) en dépend dès
// le montage. Polyfill minimal, comportement "aucun media query active".
if (!window.matchMedia) {
  window.matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  })
}

// Le rendu AntD en jsdom dépasse le délai par défaut (1 s) dès que la machine est chargée (CI, build Maven
// en parallèle) : les tests asynchrones étaient instables sans lien avec le code applicatif.
configure({ asyncUtilTimeout: 5000 })

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
