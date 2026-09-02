import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'

/**
 * Configuration TanStack Query : pas de retry sur les erreurs 4xx (une
 * ressource introuvable ou un refus d'accès ne devient pas correct en
 * réessayant), retry limité sur le reste (réseau, 5xx).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: (nombreEssais, erreur) => {
        if (erreur instanceof ApiError && erreur.status >= 400 && erreur.status < 500) {
          return false
        }
        return nombreEssais < 2
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
})
