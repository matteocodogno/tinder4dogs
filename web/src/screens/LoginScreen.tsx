type LoginScreenProps = {
  onSignIn: () => void
}

export const LoginScreen = ({ onSignIn }: LoginScreenProps) => (
  <main className="screen screen-centred">
    <section className="panel panel-narrow">
      <h1 className="title">Tinder 4 Dogs</h1>
      <p className="lede">Find your dog a friend. Sign in with the owner account first.</p>
      <button type="button" className="button button-primary" onClick={onSignIn}>
        Continue with Google
      </button>
    </section>
  </main>
)
