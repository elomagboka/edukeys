import { Navigate, createBrowserRouter } from 'react-router-dom'
import { LoginPage } from '../features/identite/pages/LoginPage'
import { EtablissementsPage } from '../features/etablissement/pages/EtablissementsPage'
import { AppLayout } from './AppLayout'
import { RouteProtegee } from './RouteProtegee'

export const router = createBrowserRouter([
  { path: '/connexion', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RouteProtegee>
        <AppLayout />
      </RouteProtegee>
    ),
    children: [
      { index: true, element: <Navigate to="/etablissements" replace /> },
      {
        path: 'etablissements',
        element: (
          <RouteProtegee rolesAutorises={['SUPER_ADMIN']}>
            <EtablissementsPage />
          </RouteProtegee>
        ),
      },
    ],
  },
])
