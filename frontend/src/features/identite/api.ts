import { useMutation } from '@tanstack/react-query'
import { apiClient, ApiError } from '../../api/client'
import { useSessionStore } from '../../stores/session'
import type { components } from '../../api/generated/schema'

type LoginRequestDto = components['schemas']['LoginRequestDto']
type JetonsReponseDto = components['schemas']['JetonsReponseDto']

async function connecter(requete: LoginRequestDto): Promise<JetonsReponseDto> {
  const { data, error } = await apiClient.POST('/api/v1/auth/login', { body: requete })
  if (error) {
    // La middleware `client.ts` transforme déjà les réponses en erreur (`ApiError`)
    // avant que openapi-fetch ne renseigne ce champ ; ce cas ne devrait pas
    // se produire en pratique, mais reste couvert pour rester honnête sur le type.
    throw new ApiError(0)
  }
  return data
}

export function useConnexion() {
  const definirSession = useSessionStore((etat) => etat.definirSession)

  return useMutation({
    mutationFn: connecter,
    onSuccess: (jetons) => {
      definirSession({
        accessToken: jetons.accessToken ?? '',
        refreshToken: jetons.refreshToken ?? '',
        etablissementId: jetons.etablissementId ?? null,
        roles: jetons.roles ?? [],
      })
    },
  })
}
