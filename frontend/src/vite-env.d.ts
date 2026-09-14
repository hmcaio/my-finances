/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL for the backend API, already including the `/api` prefix (see src/api/client.ts). */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
