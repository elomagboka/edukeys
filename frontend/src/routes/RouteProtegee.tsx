import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useSessionStore } from '../stores/session'
import type { Role } from '../shared/types/roles'

export interface RouteProtegeeProps {
  children: ReactNode
  /** Rôles autorisés. Absent = toute session authentifiée suffit. */
  rolesAutorises?: Role[]
}

/**
 * Garde de route par rôle (ergonomie uniquement — le backend reste seul
 * autorité, cf. frontend/CLAUDE.md règle 8).
 */
export function RouteProtegee({ children, rolesAutorises }: RouteProtegeeProps) {
  const accessToken = useSessionStore((etat) => etat.accessToken)
  const roles = useSessionStore((etat) => etat.roles)

  if (!accessToken) {
    return <Navigate to="/connexion" replace />
  }

  if (rolesAutorises && !roles.some((role) => rolesAutorises.includes(role as Role))) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
