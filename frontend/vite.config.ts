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
    setupFiles: ['src/test/setup.ts'],
  },
})
