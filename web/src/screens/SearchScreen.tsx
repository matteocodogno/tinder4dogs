import { GENDERS } from '../api'
import { DogCard } from '../components/DogCard'
import { useSearch } from '../hooks/useSearch'

export const SearchScreen = () => {
  const { breed, setBreed, gender, setGender, results, status, notice, run } = useSearch()

  return (
    <main className="screen">
      <section className="panel">
        <h2 className="title">Search</h2>
        <form
          className="row"
          onSubmit={(event) => {
            event.preventDefault()
            void run()
          }}
        >
          <input
            className="input"
            placeholder="Breed"
            value={breed}
            onChange={(event) => setBreed(event.target.value)}
          />
          <select
            className="input"
            value={gender}
            onChange={(event) =>
              setGender(GENDERS.find((item) => item === event.target.value) ?? '')
            }
          >
            <option value="">Any gender</option>
            {GENDERS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
          <button type="submit" className="button button-primary" disabled={status === 'searching'}>
            {status === 'searching' ? 'Searching…' : 'Search'}
          </button>
        </form>
        {notice !== null && <p className="notice">{notice}</p>}
      </section>

      {status === 'done' && results.length === 0 && (
        <p className="lede">No dog matched those filters.</p>
      )}

      {/* Rendered in the order the backend sent them, which is random. */}
      <div className="card-grid">
        {results.map((dog, index) => (
          <DogCard key={dog.id ?? `${dog.name}-${index}`} dog={dog} />
        ))}
      </div>
    </main>
  )
}
