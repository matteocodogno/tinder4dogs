import { useCallback, useEffect, useState } from 'react'
import { getProfile, putProfile } from '../api'
import type { DogSize, Gender, Profile, Temperament } from '../api'

/**
 * The form draft keeps `age` as a string so the field can be cleared while
 * typing; it is parsed on save and the backend validates the result.
 */
export type ProfileDraft = {
  ownerName: string
  name: string
  breed: string
  gender: Gender
  size: DogSize | ''
  age: string
  temperament: Temperament | ''
  preferences: string[]
}

export type ProfileStatus = 'loading' | 'ready' | 'saving' | 'saved'

const emptyDraft: ProfileDraft = {
  ownerName: '',
  name: '',
  breed: '',
  gender: 'MALE',
  size: '',
  age: '',
  temperament: '',
  preferences: [],
}

const toDraft = (profile: Profile): ProfileDraft => ({
  ownerName: profile.ownerName ?? '',
  name: profile.name,
  breed: profile.breed,
  gender: profile.gender,
  size: profile.size ?? '',
  age: String(profile.age),
  temperament: profile.temperament ?? '',
  preferences: profile.preferences,
})

const toProfile = (draft: ProfileDraft, id: number | null): Profile => {
  const age = Number.parseInt(draft.age, 10)
  return {
    id,
    ownerName: draft.ownerName.trim().length === 0 ? null : draft.ownerName.trim(),
    name: draft.name,
    breed: draft.breed,
    gender: draft.gender,
    size: draft.size === '' ? null : draft.size,
    age: Number.isFinite(age) ? age : 0,
    temperament: draft.temperament === '' ? null : draft.temperament,
    preferences: draft.preferences,
  }
}

export const useProfile = () => {
  const [draft, setDraft] = useState<ProfileDraft>(emptyDraft)
  const [profileId, setProfileId] = useState<number | null>(null)
  const [status, setStatus] = useState<ProfileStatus>('loading')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState<string | null>(null)
  const [preferenceDraft, setPreferenceDraft] = useState('')

  const load = useCallback(async () => {
    setStatus('loading')
    setFieldErrors({})
    const result = await getProfile()
    if (result.kind === 'ok') {
      setDraft(toDraft(result.data))
      setProfileId(result.data.id)
      setNotice(null)
    } else if (result.kind === 'not-found') {
      setDraft(emptyDraft)
      setProfileId(null)
      setNotice('You have no dog yet. Fill this form in and save to create one.')
    } else if (result.kind === 'failure') {
      setNotice(result.message)
    } else {
      setNotice('Could not load the profile.')
    }
    setStatus('ready')
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const updateField = useCallback(
    <K extends keyof ProfileDraft>(key: K, value: ProfileDraft[K]) => {
      setStatus('ready')
      setDraft((current) => ({ ...current, [key]: value }))
    },
    [],
  )

  const addPreference = useCallback(() => {
    const tag = preferenceDraft.trim()
    if (tag.length === 0) return
    setPreferenceDraft('')
    setDraft((current) =>
      current.preferences.includes(tag)
        ? current
        : { ...current, preferences: [...current.preferences, tag] },
    )
  }, [preferenceDraft])

  const removePreference = useCallback((tag: string) => {
    setDraft((current) => ({
      ...current,
      preferences: current.preferences.filter((item) => item !== tag),
    }))
  }, [])

  const save = useCallback(async () => {
    setStatus('saving')
    setFieldErrors({})
    setNotice(null)
    const result = await putProfile(toProfile(draft, profileId))
    if (result.kind === 'ok') {
      setDraft(toDraft(result.data))
      setProfileId(result.data.id)
      setStatus('saved')
      return
    }
    setStatus('ready')
    if (result.kind === 'validation') {
      setFieldErrors(result.errors)
      setNotice('Some fields need fixing.')
      return
    }
    if (result.kind === 'failure') {
      setNotice(result.message)
      return
    }
    if (result.kind === 'unauthorized') {
      setNotice('Your session has expired. Sign in again.')
      return
    }
    setNotice('The profile could not be saved.')
  }, [draft, profileId])

  return {
    draft,
    status,
    fieldErrors,
    notice,
    preferenceDraft,
    setPreferenceDraft,
    updateField,
    addPreference,
    removePreference,
    save,
    reload: load,
  }
}
