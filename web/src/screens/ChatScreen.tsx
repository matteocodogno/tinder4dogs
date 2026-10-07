import { useChat } from '../hooks/useChat'

export const ChatScreen = () => {
  const { entries, message, setMessage, status, notice, send } = useChat()

  return (
    <main className="screen">
      <section className="panel">
        <h2 className="title">Chat</h2>
        <p className="lede">Describe your dog in your own words and the profile fills itself in.</p>

        <ol className="transcript">
          {entries.map((entry) => (
            <li key={entry.id} className={`bubble bubble-${entry.author}`}>
              {entry.text}
            </li>
          ))}
        </ol>

        {status === 'not-implemented' && (
          <p className="notice notice-calm">
            Chatting with your profile is not built yet. The backend answers this screen with
            “501 Not Implemented” on purpose — your message was sent and understood as a request,
            there is just nothing on the other end to answer it. Nothing is broken, and nothing you
            typed was lost. Use the <strong>My dog</strong> screen to edit the profile by hand in
            the meantime.
          </p>
        )}

        {status === 'error' && notice !== null && <p className="notice">{notice}</p>}

        <form
          className="row"
          onSubmit={(event) => {
            event.preventDefault()
            void send()
          }}
        >
          {/* No maxLength and no filtering: arbitrary text goes through as typed. */}
          <textarea
            className="input textarea"
            rows={3}
            placeholder="My dog is a very calm five-year-old beagle who…"
            value={message}
            onChange={(event) => setMessage(event.target.value)}
          />
          <button type="submit" className="button button-primary" disabled={status === 'sending'}>
            {status === 'sending' ? 'Sending…' : 'Send'}
          </button>
        </form>
      </section>
    </main>
  )
}
