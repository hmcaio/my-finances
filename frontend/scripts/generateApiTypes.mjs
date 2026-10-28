// `npm run generate-api-types` (F036, ADR 0029): regenerates src/api/generated/schema.ts from the
// backend's OpenAPI document, three ways:
//
//   --offline        (no running backend, no login, no password; needs Docker and a JDK)
//                    runs the backend's `exportOpenApi` Gradle task, which boots the app against a
//                    throwaway Testcontainers Postgres for one request and writes the document,
//                    then generates from that file.
//   --from-file <f>  generates from an OpenAPI document already on disk.
//   (neither)        downloads /v3/api-docs from the running backend. It is behind its login in
//                    every profile - including the OpenAPI document, deliberately, since there is
//                    no dev bypass - so this signs in first, the way the SPA does (boot-time GET
//                    for the XSRF-TOKEN cookie, then the login POST echoing it).
//
// Credentials for the last form come from the environment, never from a file:
//   AUTH_PASSWORD=... npm run generate-api-types        (username: AUTH_USERNAME, else the
//                                                        AUTH_USERNAME line of the repo's .env)
// API_URL overrides the backend origin (default http://localhost:8080).
//   npm run generate-api-types -- --offline
//   npm run generate-api-types -- --from-file path/to/api-docs.json
// With GRADLE_BUILD_DIR set, --offline builds there (backend/gradle/relocate-build-dir.init.gradle).
import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const frontendDir = resolve(here, '..')
const backendDir = resolve(frontendDir, '..', 'backend')
const apiUrl = (process.env.API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

/** A reportable failure. Thrown, not `process.exit`ed, so a `finally` still removes the temp files. */
class Failure extends Error {}

function fail(message) {
  throw new Failure(message)
}

/** Hands an OpenAPI document on disk to openapi-typescript. */
function generate(file) {
  const bin = resolve(frontendDir, 'node_modules', 'openapi-typescript', 'bin', 'cli.js')
  execFileSync(process.execPath, [bin, file, '-o', 'src/api/generated/schema.ts'], {
    cwd: frontendDir,
    stdio: 'inherit',
  })
}

/** Runs it on a throwaway copy of the document, which is removed afterwards. */
function generateFromTemp(writeDocument) {
  const tmp = mkdtempSync(join(tmpdir(), 'my-finances-api-docs-'))
  try {
    const file = join(tmp, 'api-docs.json')
    writeDocument(file)
    generate(file)
  } finally {
    rmSync(tmp, { recursive: true, force: true })
  }
}

/** --offline: the backend's `exportOpenApi` task writes the document, no server or login needed. */
function exportOffline(file) {
  const windows = process.platform === 'win32'
  const gradleArgs = ['-q', 'exportOpenApi', `-PopenapiOutput=${file}`]
  // A root-owned or shared build directory is moved aside with GRADLE_BUILD_DIR (see the script).
  if (process.env.GRADLE_BUILD_DIR) {
    gradleArgs.unshift('-I', 'gradle/relocate-build-dir.init.gradle')
  }
  console.log('generate-api-types: exporting the OpenAPI document with Gradle (needs Docker)...')
  try {
    execFileSync(windows ? 'gradlew.bat' : './gradlew', gradleArgs, {
      cwd: backendDir,
      shell: windows,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
  } catch (error) {
    process.stderr.write(String(error.stdout ?? '') + String(error.stderr ?? ''))
    fail(
      'the exportOpenApi Gradle task failed (it starts a throwaway Postgres with Testcontainers: ' +
        'is Docker running?)',
    )
  }
  if (!existsSync(file)) fail(`exportOpenApi finished but wrote no file at ${file}`)
}

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

/** `fetch`, turning "nothing is listening" into a pointer at --offline instead of a stack trace. */
async function request(url, options) {
  try {
    return await fetch(url, options)
  } catch (error) {
    fail(
      `cannot reach ${apiUrl} (${error.cause?.code ?? error.message}): is the backend running? ` +
        'Or skip it: npm run generate-api-types -- --offline',
    )
  }
}

/** The running backend's /v3/api-docs, fetched with a session (it is behind the login). */
async function downloadFromBackend() {
  const username = process.env.AUTH_USERNAME ?? usernameFromRepoEnv()
  const password = process.env.AUTH_PASSWORD
  if (!username || !password) {
    fail(
      'the backend requires a login. Set AUTH_PASSWORD (and AUTH_USERNAME, unless it is in the ' +
        "repo's .env) for this one command, e.g.\n  AUTH_PASSWORD=... npm run generate-api-types\n" +
        'or skip the backend and the login: npm run generate-api-types -- --offline',
    )
  }

  /** Folds a response's Set-Cookie headers into the cookie jar (name -> value). */
  const jar = new Map()
  const collectCookies = (response) => {
    for (const header of response.headers.getSetCookie()) {
      const [pair] = header.split(';')
      const eq = pair.indexOf('=')
      jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim())
    }
  }
  const cookieHeader = () => [...jar].map(([name, value]) => `${name}=${value}`).join('; ')

  // 1. Any GET makes the backend hand out the XSRF-TOKEN cookie (a 401 here is expected).
  collectCookies(await request(`${apiUrl}/api/auth/session`))
  const xsrf = jar.get('XSRF-TOKEN')
  if (!xsrf) fail(`no XSRF-TOKEN cookie from ${apiUrl} - is the backend running?`)

  // 2. Log in, echoing the token as the header.
  const login = await request(`${apiUrl}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookieHeader(),
      'X-XSRF-TOKEN': xsrf,
    },
    body: JSON.stringify({ username, password }),
  })
  if (!login.ok) fail(`login failed with status ${login.status}`)
  collectCookies(login)

  // 3. Download the document with the session.
  const docs = await request(`${apiUrl}/v3/api-docs`, { headers: { Cookie: cookieHeader() } })
  if (!docs.ok) fail(`GET /v3/api-docs failed with status ${docs.status}`)
  return docs.text()
}

async function main() {
  const args = process.argv.slice(2)
  const offline = args.includes('--offline')
  const fromFileIndex = args.indexOf('--from-file')
  const fromFile = fromFileIndex >= 0 ? args[fromFileIndex + 1] : undefined
  if (fromFileIndex >= 0 && !fromFile) fail('--from-file needs a path')
  if (offline && fromFileIndex >= 0) fail('use --offline or --from-file, not both')

  if (fromFile) {
    if (!existsSync(fromFile)) fail(`no such file: ${fromFile}`)
    generate(resolve(fromFile))
  } else if (offline) {
    generateFromTemp(exportOffline)
  } else {
    const document = await downloadFromBackend()
    generateFromTemp((file) => writeFileSync(file, document))
  }
}

main().catch((error) => {
  if (!(error instanceof Failure)) throw error
  console.error(`generate-api-types: ${error.message}`)
  process.exit(1)
})
