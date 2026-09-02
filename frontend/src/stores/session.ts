import { create } from 'zustand'

/**
 * Session d'authentification : jetons + rôles courants uniquement (préférence
 * UI, jamais de donnée serveur — frontend/CLAUDE.md règle 1). Les données
 * métier (profil complet, établissement...) passent par TanStack Query.
 *
 * Volontairement NON persisté (pas de `zustand/middleware persist`) : les
 * deux jetons — y compris le refresh, valable 7 jours — ne vivent qu'en
 * mémoire JS. Un rechargement de page déconnecte. C'est un choix assumé tant
 * que la décision de domaine (issue #57) n'a pas permis de passer le refresh
 * token en cookie HttpOnly ; stocker en localStorage en attendant aurait
 * exposé le jeton le plus précieux (7 jours d'accès) à tout XSS. Voir
 * frontend/CLAUDE.md § Jetons d'authentification.
 */
export interface SessionEtat {
  accessToken: string | null
  refreshToken: string | null
  etablissementId: string | null
  roles: string[]
  definirSession: (session: {
    accessToken: string
    refreshToken: string
    etablissementId: string | null
    roles: string[]
  }) => void
  effacerSession: () => void
}

export const useSessionStore = create<SessionEtat>()((set) => ({
  accessToken: null,
  refreshToken: null,
  etablissementId: null,
  roles: [],
  definirSession: (session) =>
    set({
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      etablissementId: session.etablissementId,
      roles: session.roles,
    }),
  effacerSession: () =>
    set({ accessToken: null, refreshToken: null, etablissementId: null, roles: [] }),
}))

/** Accès à l'état hors composant React (intercepteurs `api/client.ts`). */
export const sessionStore = useSessionStore
