// Headless-Chromium driver for a RUNNING my-finances stack (real backend, no mocks).
// The `run-app` project skill (.claude/skills/run-app/SKILL.md) starts the stack and calls this file.
//
//   node scripts/drive/driveApp.mjs login
//
// Configuration comes only from the environment, so no credential or URL lives in the repo:
//   DRIVE_BASE_URL   where the SPA is served, e.g. http://localhost:5173 (dev `full` profile)
//                    or http://localhost:<FRONTEND_PORT> (prod stack). Required: no default, so a
//                    forgotten variable can never point the driver at someone else's stack.
//   AUTH_USERNAME    the login's username   (the same names the backend and generateAuthHash use)
//   AUTH_PASSWORD    the login's plaintext password for the stack under test
//   DRIVE_SHOTS_DIR  where screenshots go; default <os tmpdir>/my-finances-drive-shots
//
// Playwright is resolved from frontend/node_modules (`cd frontend && npm ci`, then
// `npx playwright install chromium` once), so scripts/ needs no package.json of its own.
//
// Reuse: import { launch, signIn, signOut, expectPath, screenshot } from './driveApp.mjs' in a
// feature-specific script, or add an entry to `flows` below. Flows fail by throwing, so the exit
// code is the verdict; read the screenshots too, a blank frame is a failed launch.
//
// Note: every wrong-password attempt counts toward the backend's 5-failure login lockout
// (HTTP 429 for several minutes), so a flow makes at most one.
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const REQUIRED_ENV = ['DRIVE_BASE_URL', 'AUTH_USERNAME', 'AUTH_PASSWORD']

/** Read and validate the environment; throws one error naming every missing variable. */
export function loadConfig(env = process.env) {
  const missing = REQUIRED_ENV.filter((name) => !env[name])
  if (missing.length > 0) {
    throw new Error(
      `Missing environment variable(s): ${missing.join(', ')}. ` +
        'See the header of scripts/drive/driveApp.mjs.',
    )
  }
  const shotsDir = env.DRIVE_SHOTS_DIR || join(tmpdir(), 'my-finances-drive-shots')
  mkdirSync(shotsDir, { recursive: true })
  return {
    baseUrl: env.DRIVE_BASE_URL.replace(/\/+$/, ''),
    username: env.AUTH_USERNAME,
    password: env.AUTH_PASSWORD,
    shotsDir,
  }
}

async function loadChromium() {
  const frontendPackage = fileURLToPath(new URL('../../frontend/package.json', import.meta.url))
  try {
    return createRequire(frontendPackage)('@playwright/test').chromium
  } catch {
    throw new Error('@playwright/test not found: run `npm ci` in frontend/ first.')
  }
}

/** Launch headless Chromium with an English locale (the UI strings the flows match on). */
export async function launch() {
  const chromium = await loadChromium()
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext({ locale: 'en-US' })
  const page = await context.newPage()
  return { browser, context, page, close: () => browser.close() }
}

/** Collect browser console errors and uncaught page errors; returns the live array. */
export function watchPage(page) {
  const errors = []
  page.on('console', (message) => message.type() === 'error' && errors.push(message.text()))
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`))
  return errors
}

/** Submit the login form (does not wait for the outcome). */
export async function submitLogin(page, username, password) {
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

/**
 * Wait until a sign-in has landed and say where: 'shell' (the app shell, which has the Log out
 * button) or 'onboarding' (a database with no accounts, App.tsx: the onboarding page sits outside
 * the shell, so it has no Log out button and does not keep the requested path).
 */
export async function waitForSignedIn(page) {
  const shell = page.getByRole('button', { name: 'Log out' })
  const onboarding = page.getByRole('heading', { name: 'Welcome to My Finances' })
  await shell.or(onboarding).first().waitFor({ timeout: 30_000 })
  return (await shell.isVisible()) ? 'shell' : 'onboarding'
}

/** Sign in with the configured credential; resolves to 'shell' or 'onboarding' (see waitForSignedIn). */
export async function signIn(page, config) {
  await page.getByRole('heading', { name: 'Sign in' }).waitFor()
  await submitLogin(page, config.username, config.password)
  return waitForSignedIn(page)
}

export async function signOut(page) {
  await page.getByRole('button', { name: 'Log out' }).click()
  await page.getByRole('heading', { name: 'Sign in' }).waitFor()
}

/** Throw unless the page is on the given pathname. */
export function expectPath(page, pathname) {
  const actual = new URL(page.url()).pathname
  if (actual !== pathname) throw new Error(`expected path ${pathname}, was ${actual}`)
}

/** Full-page screenshot into the configured directory; returns the file path. */
export async function screenshot(page, config, name) {
  const path = join(config.shotsDir, `${name}.png`)
  await page.screenshot({ path, fullPage: true })
  console.log(`screenshot ${path}`)
  return path
}

export const flows = {
  // Smoke: signed-out visit is redirected to /login, a wrong password is rejected, the right one
  // returns to the requested page, logout ends the session.
  async login(config) {
    const { page, close } = await launch()
    const errors = watchPage(page)
    try {
      await page.goto(`${config.baseUrl}/ledger`)
      await page.getByRole('heading', { name: 'Sign in' }).waitFor()
      expectPath(page, '/login')
      await screenshot(page, config, 'login-1-signed-out')

      await submitLogin(page, config.username, `${config.password}-wrong`)
      await page.getByRole('alert').waitFor()
      console.log('wrong password rejected')

      await submitLogin(page, config.username, config.password)
      const landed = await waitForSignedIn(page)
      await page.waitForLoadState('networkidle')
      await screenshot(page, config, 'login-2-signed-in')

      if (landed === 'shell') {
        expectPath(page, '/ledger')
        await signOut(page)
        expectPath(page, '/login')
      } else {
        // A fresh database: onboarding has no Log out button and drops the requested path, so the
        // return-to-/ledger and logout checks cannot run. Say so rather than pass silently.
        console.log('fresh database (onboarding shown): return-to-path and logout checks skipped')
      }
      console.log(`login flow ok; browser console errors: ${errors.length}`)
      for (const error of errors) console.log(`  ${error}`)
    } finally {
      await close()
    }
  },
}

async function main() {
  const name = process.argv[2]
  if (!flows[name]) {
    console.error(`usage: node scripts/drive/driveApp.mjs <${Object.keys(flows).join('|')}>`)
    process.exit(2)
  }
  await flows[name](loadConfig())
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error.message)
    process.exit(1)
  })
}
