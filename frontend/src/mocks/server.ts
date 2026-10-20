import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * Node-mode MSW server (F015 spec) - intercepts requests at the network level so components under
 * test call the real `apiClient` (F002's centralized Axios instance) exactly as they would in
 * production, receiving these mocked responses instead of hitting a real backend. Started/reset/
 * stopped by `src/test/setup.ts` around the test lifecycle; no per-test-file import needed.
 */
export const server = setupServer(...handlers)
