import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
// Imports `defineConfig` from `vitest/config` (a superset of Vite's own) rather than `vite`
// itself so the `test` key below type-checks - Vitest reads this file directly, there is no
// separate vitest.config.ts (F015 spec).
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    // Share the jsdom environment and module graph across test files in a worker instead of
    // rebuilding them per file (~20s -> ~12s). Safe because every file's setup (src/test/setup.ts)
    // resets MSW handlers and RTL cleanup, and no test relies on module-level state. If a test
    // ever leaks state between files, drop this line (pool: 'vmThreads' does not work here).
    isolate: false,
    setupFiles: ['src/test/setup.ts'],
    reporters: ['default', ['html', { outputDir: 'test-results' }]],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      reportsDirectory: 'coverage',
    },
  },
})
