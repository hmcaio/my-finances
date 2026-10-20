/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL for the backend API, already including the `/api` prefix (see src/api/client.ts). */
  readonly VITE_API_BASE_URL: string
  /** Logger threshold: `debug | info | warn | error | silent` (see src/utils/logger.ts). Unknown -> `warn`. */
  readonly VITE_LOG_LEVEL?: string
  /** Set only by `playwright.config.ts`'s dev server (F021): turns off the TanStack Query devtools
   * toggle button in `main.tsx`, whose fixed position can otherwise intercept a click meant for a
   * full-screen mobile dialog's own bottom-right button. */
  readonly VITE_E2E?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
