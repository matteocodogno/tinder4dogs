import { DOG_SIZES, GENDERS, TEMPERAMENTS } from '../api'
import { FieldError } from '../components/FieldError'
import { useProfile } from '../hooks/useProfile'

export const ProfileScreen = () => {
  const {
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
  } = useProfile()

  if (status === 'loading') {
    return (
      <main className="screen">
        <p className="lede">Loading your dog…</p>
      </main>
    )
  }

  return (
    <main className="screen">
      <section className="panel">
        <h2 className="title">My dog</h2>
        {notice !== null && <p className="notice">{notice}</p>}
        {status === 'saved' && <p className="notice notice-good">Saved.</p>}

        <form
          className="form"
          onSubmit={(event) => {
            event.preventDefault()
            void save()
          }}
        >
          <label className="field">
            <span className="field-label">Owner name</span>
            <input
              className="input"
              value={draft.ownerName}
              onChange={(event) => updateField('ownerName', event.target.value)}
            />
            <FieldError message={fieldErrors['ownerName']} />
          </label>

          <label className="field">
            <span className="field-label">Dog name</span>
            <input
              className="input"
              value={draft.name}
              onChange={(event) => updateField('name', event.target.value)}
            />
            <FieldError message={fieldErrors['name']} />
          </label>

          <label className="field">
            <span className="field-label">Breed</span>
            <input
              className="input"
              value={draft.breed}
              onChange={(event) => updateField('breed', event.target.value)}
            />
            <FieldError message={fieldErrors['breed']} />
          </label>

          <label className="field">
            <span className="field-label">Gender</span>
            <select
              className="input"
              value={draft.gender}
              onChange={(event) => updateField('gender', event.target.value === 'FEMALE' ? 'FEMALE' : 'MALE')}
            >
              {GENDERS.map((gender) => (
                <option key={gender} value={gender}>
                  {gender}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors['gender']} />
          </label>

          <label className="field">
            <span className="field-label">Size</span>
            <select
              className="input"
              value={draft.size}
              onChange={(event) =>
                updateField(
                  'size',
                  DOG_SIZES.find((size) => size === event.target.value) ?? '',
                )
              }
            >
              <option value="">Not set</option>
              {DOG_SIZES.map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors['size']} />
          </label>

          <label className="field">
            <span className="field-label">Age (years)</span>
            <input
              className="input"
              inputMode="numeric"
              value={draft.age}
              onChange={(event) => updateField('age', event.target.value)}
            />
            <FieldError message={fieldErrors['age']} />
          </label>

          <label className="field">
            <span className="field-label">Temperament</span>
            <select
              className="input"
              value={draft.temperament}
              onChange={(event) =>
                updateField(
                  'temperament',
                  TEMPERAMENTS.find((item) => item === event.target.value) ?? '',
                )
              }
            >
              <option value="">Not set</option>
              {TEMPERAMENTS.map((temperament) => (
                <option key={temperament} value={temperament}>
                  {temperament}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors['temperament']} />
          </label>

          <div className="field">
            <span className="field-label">Preferences</span>
            <ul className="tag-list">
              {draft.preferences.map((preference) => (
                <li key={preference} className="tag">
                  {preference}
                  <button
                    type="button"
                    className="tag-remove"
                    aria-label={`Remove ${preference}`}
                    onClick={() => removePreference(preference)}
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
            <div className="row">
              <input
                className="input"
                placeholder="long walks, other puppies, …"
                value={preferenceDraft}
                onChange={(event) => setPreferenceDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key !== 'Enter') return
                  event.preventDefault()
                  addPreference()
                }}
              />
              <button type="button" className="button" onClick={addPreference}>
                Add
              </button>
            </div>
            <FieldError message={fieldErrors['preferences']} />
          </div>

          <button type="submit" className="button button-primary" disabled={status === 'saving'}>
            {status === 'saving' ? 'Saving…' : 'Save'}
          </button>
        </form>
      </section>
    </main>
  )
}
