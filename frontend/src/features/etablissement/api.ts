import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { apiClient, ApiError } from '../../api/client'

export interface ListerEtablissementsParams {
  page: number
  taille: number
}

export function useEtablissements({ page, taille }: ListerEtablissementsParams) {
  return useQuery({
    queryKey: ['etablissements', 'liste', page, taille],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/v1/etablissements', {
        params: { query: { page, size: taille } },
      })
      if (error) {
        throw new ApiError(0)
      }
      return data
    },
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  })
}
