import Keycloak from 'keycloak-js'

export const auth = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
  realm: 'flowtrack', clientId: 'flowtrack-frontend'
})
const origin = import.meta.env.VITE_API_URL || 'http://localhost:8080'
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  await auth.updateToken(30)
  if (!auth.token) throw new Error('Session expirée. Reconnectez-vous.')
  const headers = new Headers(options.headers)
  headers.set('Authorization', 'Bearer ' + auth.token)
  if (options.body) headers.set('Content-Type', 'application/json')
  const res = await fetch(origin + '/api' + path, {...options, headers})
  if (!res.ok) throw new ApiError(res.status, res.status === 403 ? 'Accès refusé pour ce projet.' : 'La requête a échoué (HTTP ' + res.status + ').')
  return res.json()
}
