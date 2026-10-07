import { useCallback, useState } from 'react'
import { getSearch } from '../api'
import type { Dog, Gender } from '../api'

export type SearchStatus = 'idle' | 'searching' | 'done' | 'error'

export const useSearch = () => {
  const [breed, setBreed] = useState('')
  const [gender, setGender] = useState<Gender | ''>('')
  const [results, setResults] = useState<readonly Dog[]>([])
  const [status, setStatus] = useState<SearchStatus>('idle')
  const [notice, setNotice] = useState<string | null>(null)

  const run = useCallback(async () => {
    setStatus('searching')
    setNotice(null)
    const result = await getSearch({ breed, gender })
    if (result.kind === 'ok') {
      // The backend returns the dogs in random order and the UI keeps that
      // order: never sort this list.
      setResults(result.data)
      setStatus('done')
      return
    }
    setResults([])
    setStatus('error')
    if (result.kind === 'failure') {
      setNotice(result.message)
      return
    }
    if (result.kind === 'unauthorized') {
      setNotice('Your session has expired. Sign in again.')
      return
    }
    setNotice('The search could not be run.')
  }, [breed, gender])

  return { breed, setBreed, gender, setGender, results, status, notice, run }
}
