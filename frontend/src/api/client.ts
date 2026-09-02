import createClient, { type Middleware } from 'openapi-fetch'
import type { paths } from './generated/schema'
import { sessionStore } from '../stores/session'
import { estProblemDetail, type ProblemDetail } from './problemDetail'

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export const apiClient = createClient<paths>({
  baseUrl,
  // Liaison tardive : `openapi-fetch` capture `fetch` en paramètre par
  // défaut au moment de l'appel de `createClient` (donc à l'import de ce
  // module), avant que MSW ne patche `globalThis.fetch` dans les tests
  // (`beforeAll`). Sans cette indirection, les tests tapent le réseau réel.
  fetch: (...args: Parameters<typeof fetch>) => globalThis.fetch(...args),
})

/** Erreur applicative typée, portant le corps RFC 7807 quand il est présent. */
export class ApiError extends Error {
  readonly problemDetail?: ProblemDetail
  readonly status: number

  constructor(status: number, problemDetail?: ProblemDetail) {
    super(problemDetail?.detail ?? problemDetail?.title ?? `Erreur HTTP ${status}`)
    this.status = status
    this.problemDetail = problemDetail
  }
}

let rafraichissementEnCours: Promise<string | null> | null = null

async function rafraichirJeton(): Promise<string | null> {
  const refreshToken = sessionStore.getState().refreshToken
  if (!refreshToken) return null

  const reponse = await fetch(`${baseUrl}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })

  if (!reponse.ok) {
    sessionStore.getState().effacerSession()
    return null
  }

  const jetons = (await reponse.json()) as {
    accessToken: string
    refreshToken: string
    etablissementId: string | null
    roles: string[]
  }
  sessionStore.getState().definirSession(jetons)
  return jetons.accessToken
}

const authMiddleware: Middleware = {
  onRequest({ request }) {
    const token = sessionStore.getState().accessToken
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    return request
  },
  async onResponse({ request, response }) {
    const estRouteAuth =
      request.url.endsWith('/api/v1/auth/login') || request.url.endsWith('/api/v1/auth/refresh')

    if (response.status === 401 && !estRouteAuth) {
      // Un seul rafraîchissement en vol même si plusieurs requêtes échouent
      // en même temps (montage de plusieurs vues au même instant).
      rafraichissementEnCours ??= rafraichirJeton().finally(() => {
        rafraichissementEnCours = null
      })
      const nouveauToken = await rafraichissementEnCours
      if (nouveauToken) {
        const requeteReessayee = new Request(request, {
          headers: new Headers(request.headers),
        })
        requeteReessayee.headers.set('Authorization', `Bearer ${nouveauToken}`)
        return fetch(requeteReessayee)
      }
    }

    if (!response.ok) {
      let corps: unknown
      try {
        corps = await response.clone().json()
      } catch {
        corps = undefined
      }
      throw new ApiError(response.status, estProblemDetail(corps) ? corps : undefined)
    }

    return response
  },
}

apiClient.use(authMiddleware)
