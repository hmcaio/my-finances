import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '../mocks/server'
import { resetStores } from '../mocks/store'
import i18n from '../i18n/i18n'

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
  // i18next is a module-level singleton, and vite.config.ts's `isolate: false` shares the module
  // graph across test files in a worker - a test that drives a LocaleProvider toggle (or calls
  // i18next.changeLanguage directly) would otherwise leak its language into every test that runs
  // after it, in any file. 'en' matches jsdom's default navigator.language, so this is also the
  // locale every pre-F033 assertion (hardcoded English text, dot-decimal numbers) still expects.
  void i18n.changeLanguage('en')
})
afterAll(() => server.close())
