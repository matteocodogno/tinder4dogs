import type { Screen } from '../hooks/useScreen'
import { SCREENS } from '../hooks/useScreen'

const LABELS: Record<Screen, string> = {
  profile: 'My dog',
  search: 'Search',
  chat: 'Chat',
}

type NavProps = {
  current: Screen
  ownerName: string | null
  onNavigate: (screen: Screen) => void
  onSignOut: () => void
}

export const Nav = ({ current, ownerName, onNavigate, onSignOut }: NavProps) => (
  <header className="nav">
    <span className="nav-brand">Tinder 4 Dogs</span>
    <nav className="nav-links">
      {SCREENS.map((screen) => (
        <button
          key={screen}
          type="button"
          className={screen === current ? 'nav-link nav-link-active' : 'nav-link'}
          onClick={() => onNavigate(screen)}
        >
          {LABELS[screen]}
        </button>
      ))}
    </nav>
    <span className="nav-owner">{ownerName ?? 'Signed in'}</span>
    <button type="button" className="button button-quiet" onClick={onSignOut}>
      Sign out
    </button>
  </header>
)
