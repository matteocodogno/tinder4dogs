import { useCallback, useState } from 'react'
import { postProfileChat } from '../api'
import type { Profile } from '../api'

export type ChatAuthor = 'owner' | 'assistant'

export type ChatEntry = {
  id: number
  author: ChatAuthor
  text: string
}

export type ChatStatus = 'idle' | 'sending' | 'not-implemented' | 'error'

export const useChat = () => {
  const [entries, setEntries] = useState<readonly ChatEntry[]>([])
  const [message, setMessage] = useState('')
  const [status, setStatus] = useState<ChatStatus>('idle')
  const [notice, setNotice] = useState<string | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)

  const append = useCallback((author: ChatAuthor, text: string) => {
    setEntries((current) => [...current, { id: current.length + 1, author, text }])
  }, [])

  const send = useCallback(async () => {
    // No client-side filtering, trimming or length limit: whatever the owner
    // typed is exactly what the backend receives.
    if (message.length === 0) return
    const outgoing = message
    setMessage('')
    append('owner', outgoing)
    setStatus('sending')
    setNotice(null)

    const result = await postProfileChat(outgoing)

    if (result.kind === 'ok') {
      append('assistant', result.data.reply)
      setProfile(result.data.profile)
      setStatus('idle')
      return
    }
    if (result.kind === 'not-implemented') {
      // HTTP 501 is the backend's current, deliberate answer. It is not a crash
      // and not a generic error.
      setStatus('not-implemented')
      setNotice(null)
      return
    }
    setStatus('error')
    if (result.kind === 'failure') {
      setNotice(result.message)
      return
    }
    if (result.kind === 'unauthorized') {
      setNotice('Your session has expired. Sign in again.')
      return
    }
    if (result.kind === 'validation') {
      const joined = Object.values(result.errors).join(' ')
      setNotice(joined.length === 0 ? 'The message was rejected.' : joined)
      return
    }
    setNotice('The message could not be sent.')
  }, [append, message])

  return { entries, message, setMessage, status, notice, profile, send }
}
