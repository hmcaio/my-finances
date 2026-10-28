// Time to first content in headless Chromium with a fresh cache: a new browser context per run
// (the signed-in session restored from storage state), measured from navigation start to the
// moment the page's marker content exists. Needs the built frontend served same-origin with the
// API (proxy.mjs); BENCH_FRONTEND is its origin. See docs/conventions/performance.md.
//
// The time is recorded IN the page by a MutationObserver. Never measure it with Playwright's
// locator.waitFor: it polls with a backoff, which rounds every result up to ~800 ms.
import { createRequire } from 'node:module'
import { config } from './lib.mjs'

const here = new URL('.', import.meta.url)
const { chromium } = createRequire(new URL('../../frontend/package.json', here))('@playwright/test')
const { username, password } = config()
const frontend = process.env.BENCH_FRONTEND
if (!frontend) throw new Error('BENCH_FRONTEND is not set: run this through scripts/bench/run.sh')

const pages = [
  ['dashboard (account balances widget)', '/', 'text:Bench Checking'],
  ['accounts page (account row)', '/accounts', 'row:Bench Checking'],
  ['reports form (Generate button)', '/reports', 'button:Generate'],
  ['login page (no data: the floor)', '/login', 'heading:Sign in'],
]

function installMarker(marker) {
  const [kind, needle] = marker.split(':')
  const found = () => {
    if (kind === 'text') return document.body && document.body.textContent.includes(needle)
    const selector = { row: 'tr', button: 'button', heading: 'h1,h2,h3,h4,h5,h6' }[kind]
    return [...document.querySelectorAll(selector)].some((e) => e.textContent.includes(needle))
  }
  const check = () => {
    if (window.__marker === undefined && found()) window.__marker = performance.now()
  }
  new MutationObserver(check).observe(document, {
    childList: true,
    subtree: true,
    characterData: true,
  })
  document.addEventListener('DOMContentLoaded', check)
}

const browser = await chromium.launch({ headless: true })
const seed = await browser.newContext()
const seedPage = await seed.newPage()
await seedPage.goto(`${frontend}/login`)
await seedPage.getByLabel('Username').fill(username)
await seedPage.getByLabel('Password').fill(password)
await seedPage.getByRole('button', { name: 'Sign in' }).click()
await seedPage.getByRole('button', { name: 'Log out' }).waitFor({ timeout: 30000 })
const state = await seed.storageState()
await seed.close()

for (const [name, path, marker] of pages) {
  const times = []
  for (let run = 0; run < 7; run++) {
    const context = await browser.newContext(path === '/login' ? {} : { storageState: state })
    const page = await context.newPage()
    await page.addInitScript(installMarker, marker)
    await page.goto(frontend + path, { waitUntil: 'commit' })
    await page.waitForFunction(() => window.__marker !== undefined, null, { timeout: 30000 })
    times.push(await page.evaluate(() => window.__marker))
    await context.close()
  }
  const timed = times.slice(2).sort((a, b) => a - b) // the first two runs warm the machine up
  console.log(
    `${name.padEnd(38)} median ${timed[2].toFixed(0).padStart(4)} ms   max ${timed[4].toFixed(0).padStart(4)} ms`,
  )
}
await browser.close()
