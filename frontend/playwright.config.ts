import { defineConfig } from '@playwright/test'

// F020: layout checks in a real browser. The Vite dev server is the only thing that runs - every
// `/api` request is fulfilled by `page.route` (e2e/support/mockApi.ts), so there is no backend or
// Postgres. Viewports match the MUI breakpoint bands F021 uses (<600, 600-1199, >=1200).
// A dedicated port (not 5173) so a running `npm run dev` never gets picked up or clashed with.
const PORT = 5199

export default defineConfig({
  testDir: './e2e',
  // Separate from Vitest's `test-results` (its HTML report) so neither wipes the other.
  outputDir: 'e2e-results',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['html', { outputFolder: 'playwright-report', open: 'never' }], ['list']],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'on-first-retry',
  },
  webServer: {
    // Relative base URL so the app calls same-origin `/api/...`, which the route mock intercepts.
    command: `npm run dev -- --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    env: { VITE_API_BASE_URL: '/api' },
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    { name: 'mobile', use: { browserName: 'chromium', viewport: { width: 390, height: 844 } } },
    { name: 'tablet', use: { browserName: 'chromium', viewport: { width: 768, height: 1024 } } },
    { name: 'desktop', use: { browserName: 'chromium', viewport: { width: 1280, height: 800 } } },
  ],
})
