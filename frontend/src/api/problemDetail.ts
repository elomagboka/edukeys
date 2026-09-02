/**
 * Corps d'erreur RFC 7807 tel que renvoyé par
 * `GestionnaireExceptionsGlobal` (backend, module common). Pas de type
 * généré : les erreurs ne figurent pas dans le contrat OpenAPI (elles ne
 * sont pas déclarées comme schéma de réponse par les contrôleurs), donc ce
 * type est écrit à la main en miroir exact de `ProblemDetail` — ce n'est pas
 * un DTO de succès.
 */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  correlationId?: string
  champsInvalides?: string[]
}

function estObjet(valeur: unknown): valeur is Record<string, unknown> {
  return typeof valeur === 'object' && valeur !== null
}

export function estProblemDetail(valeur: unknown): valeur is ProblemDetail {
  return estObjet(valeur) && ('detail' in valeur || 'title' in valeur || 'status' in valeur)
}

/**
 * Reconnaît une `ApiError` (api/client.ts) par duck-typing plutôt que par
 * `instanceof` : `problemDetail.ts` est importé par `client.ts`, un import
 * inverse créerait un cycle.
 */
function estApiError(valeur: unknown): valeur is { problemDetail?: ProblemDetail; message: string } {
  return valeur instanceof Error && 'problemDetail' in valeur
}

/** Traduit un corps d'erreur RFC 7807 (ou une erreur réseau) en message utilisateur. */
export function messageErreur(erreur: unknown): string {
  if (estApiError(erreur)) {
    if (erreur.problemDetail) {
      return formaterProblemDetail(erreur.problemDetail)
    }
    return erreur.message
  }
  if (estProblemDetail(erreur)) {
    return formaterProblemDetail(erreur)
  }
  if (erreur instanceof Error) {
    return erreur.message
  }
  return 'Une erreur inattendue est survenue. Vérifiez votre connexion et réessayez.'
}

function formaterProblemDetail(problemDetail: ProblemDetail): string {
  const champs = problemDetail.champsInvalides?.length
    ? ` (${problemDetail.champsInvalides.join(', ')})`
    : ''
  return `${problemDetail.detail ?? problemDetail.title ?? 'Une erreur est survenue.'}${champs}`
}
