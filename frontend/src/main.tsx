import { lazy, StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App'
import { createQueryClient } from './api/core/queryClient'
import { installGlobalErrorLogging, reactRootErrorHandlers } from './utils/globalErrorLogging'

installGlobalErrorLogging()

const queryClient = createQueryClient()

// Dev only: the `import.meta.env.DEV` guard is replaced by `false` in a production build, so the
// dynamic import (and the whole devtools package) is dropped from the bundle. Also off under
// Playwright (`VITE_E2E`, set only by `playwright.config.ts`'s dev server): its floating toggle
// button is fixed-position and can sit over a full-screen mobile dialog's own bottom-right button
// (e.g. a `ResponsiveFilterBar` sheet's "Done"), intercepting the click (F021).
const ReactQueryDevtools =
  import.meta.env.DEV && !import.meta.env.VITE_E2E
    ? lazy(() =>
        import('@tanstack/react-query-devtools').then((m) => ({ default: m.ReactQueryDevtools })),
      )
    : null

createRoot(document.getElementById('root')!, reactRootErrorHandlers).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
      {ReactQueryDevtools && (
        <Suspense fallback={null}>
          <ReactQueryDevtools initialIsOpen={false} />
        </Suspense>
      )}
    </QueryClientProvider>
  </StrictMode>,
)
