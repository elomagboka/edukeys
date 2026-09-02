import { http, HttpResponse } from 'msw'

const baseUrl = 'http://localhost:8080'

export const handlers = [
  http.post(`${baseUrl}/api/v1/auth/login`, async ({ request }) => {
    const corps = (await request.json()) as { email: string; motDePasse: string }
    if (corps.email === 'super.admin@edukeys.tg' && corps.motDePasse === 'Password123!') {
      return HttpResponse.json({
        accessToken: 'access-test',
        refreshToken: 'refresh-test',
        expiresDansSecondes: 900,
        etablissementId: null,
        roles: ['SUPER_ADMIN'],
      })
    }
    return HttpResponse.json(
      { title: 'Non autorisé', status: 401, detail: 'Identifiants invalides.' },
      { status: 401 },
    )
  }),

  http.get(`${baseUrl}/api/v1/etablissements`, () => {
    return HttpResponse.json({
      contenu: [
        {
          id: '01977000-0000-7000-9000-000000000001',
          code: 'EDU-001',
          nom: 'Établissement Démo',
          sigle: 'ED',
          typeEtablissement: 'COLLEGE',
          ville: 'Lomé',
          actif: true,
          nombreSites: 1,
        },
      ],
      page: 0,
      taille: 10,
      totalElements: 1,
      totalPages: 1,
    })
  }),
]
