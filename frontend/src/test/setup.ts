import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '../mocks/server'
import { resetStores } from '../mocks/store'

// Vitest global setup (wired via vite.config.ts's `test.setupFiles`, F015 spec): every test file
// gets jest-dom's matchers and the MSW server's lifecycle automatically, with no per-file import.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  // React Testing Library normally auto-registers this itself via a global `afterEach`, but that
  // only happens with Vitest's `globals: true` - this project keeps `globals` off (test functions
  // are imported explicitly, per the rest of the config), so unmount/reset the DOM ourselves.
  cleanup()
  server.resetHandlers()
  resetStores()
})
afterAll(() => server.close())
