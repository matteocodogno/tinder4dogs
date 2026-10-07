// The entire HTTP surface of the Tinder 4 Dogs backend lives in this file.
// If the backend disagrees with the contract, this is the only file to change.

/* ------------------------------------------------------------------ *
 * Domain types (mirror of the backend contract)
 * ------------------------------------------------------------------ */

export type Gender = 'MALE' | 'FEMALE'
export type DogSize = 'SMALL' | 'MEDIUM' | 'LARGE'
export type Temperament = 'CALM' | 'PLAYFUL' | 'ANXIOUS' | 'SOCIABLE' | 'PROTECTIVE'

export type Profile = {
  id: number | null
  ownerName: string | null
  name: string
  breed: string
  gender: Gender
  size: DogSize | null
  age: number
  temperament: Temperament | null
  preferences: string[]
}

/**
 * `GET /api/search` is specified as returning `Dog[]`, but the contract does not
 * give the shape of `Dog`. It is read here as the same record as `Profile`.
 * If the backend returns a narrower dog record, narrow this alias.
 */
export type Dog = Profile

export type Me = {
  authenticated: boolean
  ownerName: string | null
  email: string | null
}

export type ChatReply = {
  reply: string
  profile: Profile | null
}

export type SearchFilters = {
  breed: string
  gender: Gender | ''
}

export const GENDERS: readonly Gender[] = ['MALE', 'FEMALE']
export const DOG_SIZES: readonly DogSize[] = ['SMALL', 'MEDIUM', 'LARGE']
export const TEMPERAMENTS: readonly Temperament[] = [
  'CALM',
  'PLAYFUL',
  'ANXIOUS',
  'SOCIABLE',
  'PROTECTIVE',
]

/* ------------------------------------------------------------------ *
 * Result type: every expected HTTP status is a value, not an exception
 * ------------------------------------------------------------------ */

export type ApiResult<T> =
  | { kind: 'ok'; data: T }
  | { kind: 'unauthorized' }
  | { kind: 'not-found' }
  | { kind: 'validation'; errors: Record<string, string> }
  | { kind: 'not-implemented' }
  | { kind: 'failure'; status: number | null; message: string }

/* ------------------------------------------------------------------ *
 * Transport
 * ------------------------------------------------------------------ */

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/** Spring Security's SPA CSRF preset writes this readable cookie. */
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

const readCookie = (name: string): string | null => {
  const prefix = `${name}=`
  const hit = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return hit === undefined ? null : decodeURIComponent(hit.slice(prefix.length))
}

const readBody = async (response: Response): Promise<unknown> => {
  const text = await response.text()
  if (text.length === 0) return null
  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}

const toErrorMap = (value: unknown): Record<string, string> => {
  if (typeof value !== 'object' || value === null) return {}
  const result: Record<string, string> = {}
  for (const [field, message] of Object.entries(value as Record<string, unknown>)) {
    if (typeof message === 'string') result[field] = message
  }
  return result
}

/** `400` bodies look like `{"errors":{"<field>":"<message>"}}`. */
const extractErrors = (body: unknown): Record<string, string> => {
  if (typeof body !== 'object' || body === null) return {}
  return toErrorMap((body as Record<string, unknown>)['errors'])
}

type Call = {
  path: string
  method: 'GET' | 'POST' | 'PUT'
  body?: unknown
  expectBody: boolean
}

const call = async (input: Call): Promise<ApiResult<unknown>> => {
  const headers: Record<string, string> = {}
  if (input.body !== undefined) headers['Content-Type'] = 'application/json'
  // Every mutating request carries the CSRF token read back from the cookie.
  if (input.method !== 'GET') {
    const token = readCookie(CSRF_COOKIE)
    if (token !== null) headers[CSRF_HEADER] = token
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${input.path}`, {
      method: input.method,
      headers,
      // Auth is a session cookie and the SPA is on another origin.
      credentials: 'include',
      ...(input.body === undefined ? {} : { body: JSON.stringify(input.body) }),
    })
  } catch {
    return {
      kind: 'failure',
      status: null,
      message: `Could not reach the backend at ${API_BASE_URL}.`,
    }
  }

  if (response.status === 401) return { kind: 'unauthorized' }
  if (response.status === 404) return { kind: 'not-found' }
  if (response.status === 501) return { kind: 'not-implemented' }
  if (response.status === 400) {
    return { kind: 'validation', errors: extractErrors(await readBody(response)) }
  }
  if (!response.ok) {
    return {
      kind: 'failure',
      status: response.status,
      message: `The backend replied with HTTP ${response.status}.`,
    }
  }
  if (!input.expectBody) return { kind: 'ok', data: null }
  return { kind: 'ok', data: await readBody(response) }
}

/**
 * The single place where an untyped response body becomes a typed one. There is
 * no runtime validator on purpose: the contract above is the agreement, and a
 * schema library would be another dependency.
 */
const okAs = <T>(result: ApiResult<unknown>): ApiResult<T> =>
  result.kind === 'ok' ? { kind: 'ok', data: result.data as T } : result

/* ------------------------------------------------------------------ *
 * Endpoints
 * ------------------------------------------------------------------ */

/** Full page navigation target for the login button, never a fetch. */
export const googleOAuthUrl = (): string => `${API_BASE_URL}/oauth2/authorization/google`

export const getMe = async (): Promise<ApiResult<Me>> =>
  okAs<Me>(await call({ path: '/api/me', method: 'GET', expectBody: true }))

export const postLogout = async (): Promise<ApiResult<null>> =>
  okAs<null>(await call({ path: '/logout', method: 'POST', expectBody: false }))

export const getProfile = async (): Promise<ApiResult<Profile>> =>
  okAs<Profile>(await call({ path: '/api/profile', method: 'GET', expectBody: true }))

export const putProfile = async (profile: Profile): Promise<ApiResult<Profile>> =>
  okAs<Profile>(
    await call({ path: '/api/profile', method: 'PUT', body: profile, expectBody: true }),
  )

export const getSearch = async (filters: SearchFilters): Promise<ApiResult<Dog[]>> => {
  const query = new URLSearchParams()
  if (filters.breed.trim().length > 0) query.set('breed', filters.breed.trim())
  if (filters.gender !== '') query.set('gender', filters.gender)
  const serialised = query.toString()
  const suffix = serialised.length === 0 ? '' : `?${serialised}`
  return okAs<Dog[]>(await call({ path: `/api/search${suffix}`, method: 'GET', expectBody: true }))
}

export const postProfileChat = async (message: string): Promise<ApiResult<ChatReply>> =>
  okAs<ChatReply>(
    await call({ path: '/api/profile/chat', method: 'POST', body: { message }, expectBody: true }),
  )
