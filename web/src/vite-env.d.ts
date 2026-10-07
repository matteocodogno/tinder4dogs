/// <reference types="vite/client" />

// Global augmentation of Vite's env type. This is the one place an `interface`
// is used instead of a `type`: declaration merging into a global interface is
// the only way to type `import.meta.env`, and a `type` alias cannot do it.
interface ImportMetaEnv {
  /** Base URL of the Tinder 4 Dogs backend. Defaults to http://localhost:8080 */
  readonly VITE_API_BASE_URL?: string
}
