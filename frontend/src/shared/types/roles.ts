/**
 * Rôles applicatifs d'Edukeys — HYPOTHÈSE documentée : recopiés de
 * `RoleCode` (backend, module identite, T-04) au moment de T-11. Pas un DTO
 * serveur (`shared/types` ne doit jamais en contenir) : c'est le nom exact
 * d'une union fermée déjà stable côté backend, utilisé uniquement pour les
 * gardes de route et le filtrage de menu. À revalider si `RoleCode` change.
 */
export const ROLES = [
  'SUPER_ADMIN',
  'ADMIN',
  'DIRECTION',
  'GESTIONNAIRE',
  'ENSEIGNANT',
  'PARENT',
  'ELEVE',
] as const

export type Role = (typeof ROLES)[number]

export function estRoleConnu(valeur: string): valeur is Role {
  return (ROLES as readonly string[]).includes(valeur)
}
