// `npm run generate-api-types` (F036, ADR 0029): regenerates src/api/generated/schema.ts from the
// running backend's /v3/api-docs. The backend is behind its login in every profile - including
// the OpenAPI document, deliberately, since there is no dev bypass - so this signs in first, the
// way the SPA does (boot-time GET for the XSRF-TOKEN cookie, then the login POST echoing it),
// downloads the document with that session, and hands the file to openapi-typescript.
//
// Credentials come from the environment, never from a file:
//   AUTH_PASSWORD=... npm run generate-api-types        (username: AUTH_USERNAME, else the
//                                                        AUTH_USERNAME line of the repo's .env)
// API_URL overrides the backend origin (default http://localhost:8080).
import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const frontendDir = resolve(here, '..')
const apiUrl = (process.env.API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

function usernameFromRepoEnv() {
  const envFile = resolve(frontendDir, '..', '.env')
  if (!existsSync(envFile)) return undefined
  const line = readFileSync(envFile, 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith('AUTH_USERNAME='))
  return line
    ?.slice('AUTH_USERNAME='.length)
    .trim()
    .replace(/^['"]|['"]$/g, '')
}

const username = process.env.AUTH_USERNAME ?? usernameFromRepoEnv()
const password = process.env.AUTH_PASSWORD
if (!username || !password) {
  console.error(
    'generate-api-types: the backend requires a login. Set AUTH_PASSWORD (and AUTH_USERNAME, unless ' +
      "it is in the repo's .env) for this one command, e.g.\n  AUTH_PASSWORD=... npm run generate-api-types",
  )
  process.exit(1)
}

/** Folds a response's Set-Cookie headers into the cookie jar (name -> value). */
function collectCookies(jar, response) {
  for (const header of response.headers.getSetCookie()) {
    const [pair] = header.split(';')
    const eq = pair.indexOf('=')
    jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim())
  }
}

const cookieHeader = (jar) => [...jar].map(([name, value]) => `${name}=${value}`).join('; ')

const jar = new Map()

// 1. Any GET makes the backend hand out the XSRF-TOKEN cookie (a 401 here is expected).
collectCookies(jar, await fetch(`${apiUrl}/api/auth/session`))
const xsrf = jar.get('XSRF-TOKEN')
if (!xsrf) {
  console.error(`generate-api-types: no XSRF-TOKEN cookie from ${apiUrl} - is the backend running?`)
  process.exit(1)
}

// 2. Log in, echoing the token as the header.
const login = await fetch(`${apiUrl}/api/auth/login`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Cookie: cookieHeader(jar),
    'X-XSRF-TOKEN': xsrf,
  },
  body: JSON.stringify({ username, password }),
})
if (!login.ok) {
  console.error(`generate-api-types: login failed with status ${login.status}`)
  process.exit(1)
}
collectCookies(jar, login)

// 3. Download the document with the session and generate from the local copy.
const docs = await fetch(`${apiUrl}/v3/api-docs`, { headers: { Cookie: cookieHeader(jar) } })
if (!docs.ok) {
  console.error(`generate-api-types: GET /v3/api-docs failed with status ${docs.status}`)
  process.exit(1)
}
const tmp = mkdtempSync(join(tmpdir(), 'my-finances-api-docs-'))
try {
  const file = join(tmp, 'api-docs.json')
  writeFileSync(file, await docs.text())
  const bin = resolve(frontendDir, 'node_modules', 'openapi-typescript', 'bin', 'cli.js')
  execFileSync(process.execPath, [bin, file, '-o', 'src/api/generated/schema.ts'], {
    cwd: frontendDir,
    stdio: 'inherit',
  })
} finally {
  rmSync(tmp, { recursive: true, force: true })
}
