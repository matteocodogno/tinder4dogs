import { Nav } from './components/Nav'
import { ChatScreen } from './screens/ChatScreen'
import { LoginScreen } from './screens/LoginScreen'
import { ProfileScreen } from './screens/ProfileScreen'
import { SearchScreen } from './screens/SearchScreen'
import { useScreen } from './hooks/useScreen'
import { useSession } from './hooks/useSession'

export const App = () => {
  const { state, reload, signIn, signOut } = useSession()
  const { screen, navigate } = useScreen()

  if (state.status === 'loading') {
    return (
      <main className="screen screen-centred">
        <p className="lede">Checking your session…</p>
      </main>
    )
  }

  if (state.status === 'error') {
    return (
      <main className="screen screen-centred">
        <section className="panel panel-narrow">
          <h1 className="title">Tinder 4 Dogs</h1>
          <p className="notice">{state.message}</p>
          <button type="button" className="button" onClick={() => void reload()}>
            Try again
          </button>
        </section>
      </main>
    )
  }

  if (state.status === 'signed-out') {
    return <LoginScreen onSignIn={signIn} />
  }

  return (
    <>
      <Nav
        current={screen}
        ownerName={state.me.ownerName}
        onNavigate={navigate}
        onSignOut={() => void signOut()}
      />
      {screen === 'profile' && <ProfileScreen />}
      {screen === 'search' && <SearchScreen />}
      {screen === 'chat' && <ChatScreen />}
    </>
  )
}
