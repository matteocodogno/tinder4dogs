import { useCallback, useEffect, useState } from 'react'

export type Screen = 'profile' | 'search' | 'chat'

export const SCREENS: readonly Screen[] = ['profile', 'search', 'chat']

const isScreen = (value: string): value is Screen =>
  value === 'profile' || value === 'search' || value === 'chat'

const fromHash = (hash: string): Screen => {
  const name = hash.replace(/^#\/?/, '')
  return isScreen(name) ? name : 'profile'
}

/** Hash-based screen switching. No router library, on purpose. */
export const useScreen = () => {
  const [screen, setScreen] = useState<Screen>(() => fromHash(window.location.hash))

  useEffect(() => {
    const onHashChange = () => setScreen(fromHash(window.location.hash))
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const navigate = useCallback((next: Screen) => {
    window.location.hash = `#/${next}`
  }, [])

  return { screen, navigate }
}
