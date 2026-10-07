import type { Dog } from '../api'

type DogCardProps = {
  dog: Dog
}

export const DogCard = ({ dog }: DogCardProps) => (
  <article className="card">
    <h3 className="card-title">{dog.name}</h3>
    <dl className="card-facts">
      <dt>Breed</dt>
      <dd>{dog.breed}</dd>
      <dt>Gender</dt>
      <dd>{dog.gender}</dd>
      <dt>Age</dt>
      <dd>{dog.age}</dd>
      <dt>Size</dt>
      <dd>{dog.size ?? '—'}</dd>
      <dt>Temperament</dt>
      <dd>{dog.temperament ?? '—'}</dd>
      <dt>Owner</dt>
      <dd>{dog.ownerName ?? '—'}</dd>
    </dl>
    {dog.preferences.length > 0 && (
      <ul className="tag-list">
        {dog.preferences.map((preference) => (
          <li key={preference} className="tag">
            {preference}
          </li>
        ))}
      </ul>
    )}
  </article>
)
