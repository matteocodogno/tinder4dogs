# Tinder 4 Dogs — web

The single-page UI for the Tinder 4 Dogs backend: Vite + React + TypeScript, plain
`fetch`, plain CSS. No router, no component library, no state library.

## Run it

```bash
npm ci        # or: npm install
npm run dev   # http://localhost:5173
```

```bash
npm run build      # type-check, then production build into dist/
npm run typecheck  # tsc --noEmit only
```

## Configuration

| Variable            | Default                 | Meaning                           |
| ------------------- | ----------------------- | --------------------------------- |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Base URL of the backend           |

Copy `.env.example` to `.env.local` to override it. The SPA expects the backend to
be running on **port 8080** — start it first, otherwise every screen reports that it
cannot reach the backend.

Because the SPA and the backend are on different origins, every request is sent with
`credentials: 'include'` (the session is a cookie) and every non-`GET` request echoes
the `XSRF-TOKEN` cookie back in the `X-XSRF-TOKEN` header. The backend therefore has
to allow this origin with credentials in its CORS configuration.

## Where things are

```
src/api.ts        the whole backend contract: types + every endpoint
src/hooks/        all the logic, one hook per file
src/screens/      one screen component per file
src/components/   shared render-only pieces
src/styles.css    all the styling
```

Screens are switched by the URL hash (`#/profile`, `#/search`, `#/chat`) and by
whether `GET /api/me` says the owner is signed in.

## A note on this SPA during the course

This front end is handed to participants as the UI their backend has to serve, and it
is **not edited during the course** — the exercises are on the backend side. It is
deliberately small and dependency-light so it stays readable and keeps building without
anybody maintaining it. If the backend ends up disagreeing with the contract, the
reconciliation happens in `src/api.ts` and nowhere else.

The chat screen is the exception worth knowing about: `POST /api/profile/chat` answers
`501 Not Implemented` in the current backend, on purpose, and the screen renders that
as a plain explanation rather than an error. Implementing that endpoint is the point of
a later exercise; the screen already handles the `200` shape too.
