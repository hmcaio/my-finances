import { lazy, StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'
import { createQueryClient } from './api/queryClient'
import { installGlobalErrorLogging, reactRootErrorHandlers } from './utils/globalErrorLogging'

installGlobalErrorLogging()

const queryClient = createQueryClient()

// Dev only: the `import.meta.env.DEV` guard is replaced by `false` in a production build, so the
// dynamic import (and the whole devtools package) is dropped from the bundle.
const ReactQueryDevtools = import.meta.env.DEV
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
