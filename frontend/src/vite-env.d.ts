/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL for the backend API, already including the `/api` prefix (see src/api/client.ts). */
  readonly VITE_API_BASE_URL: string
  /** Logger threshold: `debug | info | warn | error | silent` (see src/utils/logger.ts). Unknown -> `warn`. */
  readonly VITE_LOG_LEVEL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
