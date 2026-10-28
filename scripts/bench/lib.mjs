// Shared plumbing for the scripts/bench tools: configuration from the environment that run.sh
// sets, a signed-in HTTP client, and dates relative to today.
import { spawnSync } from 'node:child_process'

export function config() {
  const need = (name) => {
    const value = process.env[name]
    if (!value) throw new Error(`${name} is not set: run these tools through scripts/bench/run.sh`)
    return value
  }
  return {
    api: process.env.BENCH_API ?? need('BENCH_API'),
    username: process.env.BENCH_USERNAME ?? 'bench',
    password: need('BENCH_PASSWORD'),
  }
}

/**
 * Signs in the way the SPA does (a GET hands out the XSRF-TOKEN cookie, the login POST echoes it)
 * and returns `call(method, path, body)`. The cookie jar is refreshed from EVERY response: the
 * backend rotates the CSRF token at login, so the first POST after it is answered 403 unless the
 * client re-reads XSRF-TOKEN from the login response.
 */
export async function signIn(base, username, password) {
  const jar = new Map()
  const collect = (response) => {
    for (const header of response.headers.getSetCookie()) {
      const [pair] = header.split(';')
      const eq = pair.indexOf('=')
      jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim())
    }
  }
  const cookies = () => [...jar].map(([name, value]) => `${name}=${value}`).join('; ')
  collect(await fetch(`${base}/api/auth/session`))
  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookies(),
      'X-XSRF-TOKEN': jar.get('XSRF-TOKEN'),
    },
    body: JSON.stringify({ username, password }),
  })
  if (!login.ok) throw new Error(`login against ${base} failed with status ${login.status}`)
  collect(login)
  return async function call(method, path, body) {
    const started = performance.now()
    const response = await fetch(base + path, {
      method,
      headers: {
        Cookie: cookies(),
        'X-XSRF-TOKEN': jar.get('XSRF-TOKEN') ?? '',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
    collect(response)
    const bytes = Buffer.from(await response.arrayBuffer())
    return { status: response.status, bytes, ms: performance.now() - started }
  }
}

const iso = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

/** Today and the dates the trend endpoints are asked about. */
export function dates() {
  const today = new Date()
  const yearsAgo = (n) => new Date(today.getFullYear() - n, today.getMonth(), today.getDate())
  return { today: iso(today), oneYearAgo: iso(yearsAgo(1)), fiveYearsAgo: iso(yearsAgo(5)) }
}

/** psql inside the benchmark's Postgres container; returns stdout. */
export function psql(sql) {
  const container = process.env.BENCH_PG_CONTAINER
  const result = spawnSync(
    'docker',
    ['exec', container, 'psql', '-U', 'myfinances', '-d', 'myfinances', '-qAt', '-c', sql],
    { encoding: 'utf8' },
  )
  if (result.status !== 0) throw new Error(`psql failed: ${result.stderr}`)
  return result.stdout
}

/** Everything the container's Postgres has logged so far (it logs to stderr). */
export function postgresLog() {
  const result = spawnSync('docker', ['logs', process.env.BENCH_PG_CONTAINER], {
    encoding: 'utf8',
    maxBuffer: 1 << 30,
  })
  return result.stdout + result.stderr
}
