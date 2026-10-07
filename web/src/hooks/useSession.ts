import { useCallback, useEffect, useState } from 'react'
import { getMe, googleOAuthUrl, postLogout } from '../api'
import type { Me } from '../api'

export type SessionState =
  | { status: 'loading' }
  | { status: 'signed-in'; me: Me }
  | { status: 'signed-out' }
  | { status: 'error'; message: string }

/** Decides, from `GET /api/me`, whether the SPA shows login or the app. */
export const useSession = () => {
  const [state, setState] = useState<SessionState>({ status: 'loading' })

  const reload = useCallback(async () => {
    setState({ status: 'loading' })
    const result = await getMe()
    if (result.kind === 'ok' && result.data.authenticated) {
      setState({ status: 'signed-in', me: result.data })
      return
    }
    if (result.kind === 'ok' || result.kind === 'unauthorized') {
      setState({ status: 'signed-out' })
      return
    }
    if (result.kind === 'failure') {
      setState({ status: 'error', message: result.message })
      return
    }
    setState({ status: 'error', message: 'Unexpected reply from /api/me.' })
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  /** Full page navigation to the backend's OAuth entry point. */
  const signIn = useCallback(() => {
    window.location.assign(googleOAuthUrl())
  }, [])

  const signOut = useCallback(async () => {
    await postLogout()
    setState({ status: 'signed-out' })
  }, [])

  return { state, reload, signIn, signOut }
}
