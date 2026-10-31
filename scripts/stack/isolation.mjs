// What makes a second stack independent of the real ones, as rules over the RESOLVED compose config
// (`docker compose config --format json`). docker-compose.yml and docker-compose.prod.yml build
// every pinned name from STACK_NAME (default my-finances) and every dev host port from a variable
// (defaults 5432, 5050, 8080, 5173), so a stack started with its own STACK_NAME and ports shares no
// container, volume or port with the real dev or prod stack (docs/conventions/tooling.md). The
// tests in isolation.test.mjs render both files twice and apply these rules, so a name or port
// pinned in a compose file later fails CI instead of surfacing as a collision with real prod.
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

/** The names and ports of the real stacks: what the files render with no variable set. */
export const REAL = {
  dev: {
    project: 'my-finances-dev',
    containers: {
      postgres: 'my-finances-postgres',
      pgadmin: 'my-finances-pgadmin',
      backend: 'my-finances-backend',
      frontend: 'my-finances-frontend',
    },
    ports: { postgres: 5432, pgadmin: 5050, backend: 8080, frontend: 5173 },
  },
  prod: {
    containers: {
      postgres: 'my-finances-postgres-prod',
      backup: 'my-finances-backup-prod',
      backend: 'my-finances-backend-prod',
      frontend: 'my-finances-frontend-prod',
    },
    // The volume keys are the real names themselves (services mount them by key); only `name:` follows STACK_NAME.
    volumes: ['my-finances-logs-prod', 'my-finances-backups-prod'],
    frontendPort: 8081,
  },
}

const publishedPorts = (service) => (service?.ports ?? []).map((p) => Number(p.published))

/** Violations of "renders exactly the real stack" for a config rendered with no variable set. */
export function defaultViolations(kind, config) {
  const found = []
  const real = REAL[kind]
  for (const [service, name] of Object.entries(real.containers)) {
    const actual = config.services?.[service]?.container_name
    if (actual !== name) found.push(`${service}: container_name is '${actual}', the real stack's is '${name}'`)
  }
  if (kind === 'dev') {
    if (config.name !== real.project) found.push(`project name is '${config.name}', expected '${real.project}'`)
    for (const [service, port] of Object.entries(real.ports)) {
      if (!publishedPorts(config.services?.[service]).includes(port)) {
        found.push(`${service}: does not publish host port ${port} by default`)
      }
    }
    const cors = config.services?.backend?.environment?.MYFINANCES_CORS_ALLOWED_ORIGINS ?? ''
    if (!cors.includes('http://localhost:5173')) found.push(`backend CORS origins '${cors}' lack http://localhost:5173`)
    const api = config.services?.frontend?.environment?.VITE_API_BASE_URL
    if (api !== undefined && api !== 'http://localhost:8080/api') found.push(`frontend VITE_API_BASE_URL is '${api}'`)
  } else {
    for (const key of real.volumes) {
      const actual = config.volumes?.[key]?.name
      if (actual !== key) found.push(`volume ${key}: named '${actual}', the real stack's is '${key}'`)
    }
    if (!publishedPorts(config.services?.frontend).includes(real.frontendPort)) {
      found.push(`frontend: does not publish host port ${real.frontendPort} by default`)
    }
  }
  return found
}

/**
 * Violations of "shares nothing with the real stacks" for a config rendered with STACK_NAME=`stack`
 * and, for dev, the four chosen host ports `ports` ({postgres, pgadmin, backend, frontend}); for
 * prod the frontend's `frontendPort`.
 */
export function isolatedViolations(kind, config, { stack, ports, frontendPort }) {
  const found = []
  const real = REAL[kind]
  const own = (name) => typeof name === 'string' && name.startsWith(`${stack}-`)
  for (const [service, def] of Object.entries(config.services ?? {})) {
    if (!def.container_name) continue
    if (!own(def.container_name)) found.push(`${service}: container_name '${def.container_name}' is not under '${stack}-'`)
  }
  for (const service of Object.keys(real.containers)) {
    if (!config.services?.[service]) found.push(`${service}: missing from the rendered config`)
  }
  for (const [key, volume] of Object.entries(config.volumes ?? {})) {
    const ok = own(volume.name) || (!volume.external && String(volume.name).startsWith(`${config.name}_`))
    if (!ok) found.push(`volume ${key}: named '${volume.name}', neither under '${stack}-' nor scoped to project '${config.name}'`)
  }
  if (kind === 'dev') {
    if (!own(config.name)) found.push(`project name '${config.name}' is not under '${stack}-'`)
    for (const [service, port] of Object.entries(ports)) {
      const published = publishedPorts(config.services?.[service])
      if (!published.includes(Number(port)) || published.length !== 1) {
        found.push(`${service}: publishes [${published}], expected only ${port}`)
      }
    }
    const cors = config.services?.backend?.environment?.MYFINANCES_CORS_ALLOWED_ORIGINS ?? ''
    if (!cors.includes(`:${ports.frontend}`) || cors.includes(':5173')) {
      found.push(`backend CORS origins '${cors}' do not follow the frontend port ${ports.frontend}`)
    }
    const api = config.services?.frontend?.environment?.VITE_API_BASE_URL
    if (api !== `http://localhost:${ports.backend}/api`) {
      found.push(`frontend VITE_API_BASE_URL is '${api}', expected http://localhost:${ports.backend}/api`)
    }
  } else {
    const published = publishedPorts(config.services?.frontend)
    if (published.length !== 1 || published[0] !== Number(frontendPort)) {
      found.push(`frontend publishes [${published}], expected only ${frontendPort}`)
    }
  }
  return found
}

// Variables the files read; stripped from the caller's environment so the render depends only on
// what is passed here (the shell's value would win over --env-file).
const VARIABLES = [
  'STACK_NAME', 'POSTGRES_PORT', 'PGADMIN_PORT', 'BACKEND_PORT', 'FRONTEND_DEV_PORT', 'FRONTEND_PORT',
  'BIND_ADDR', 'BACKUP_DIR', 'IMAGE_TAG', 'COMPOSE_PROJECT_NAME',
]

/** `docker compose config --format json` of one file with exactly `variables` set. Needs Docker Compose. */
export function render(file, variables = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'isolation-'))
  try {
    const env = {
      AUTH_USERNAME: 'u', AUTH_PASSWORD_HASH: 'h', POSTGRES_PASSWORD: 'p', IMAGE_TAG: 'test', ...variables,
    }
    writeFileSync(join(dir, 'env'), Object.entries(env).map(([k, v]) => `${k}=${v}`).join('\n') + '\n')
    const clean = { ...process.env }
    for (const key of VARIABLES) delete clean[key]
    const out = execFileSync(
      'docker',
      ['compose', '-f', file, '--env-file', join(dir, 'env'), '--profile', '*', 'config', '--format', 'json'],
      { env: clean, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] },
    )
    return JSON.parse(out)
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

export function composeAvailable() {
  try {
    execFileSync('docker', ['compose', 'version', '--short'], { stdio: 'ignore' })
    return true
  } catch {
    return false
  }
}
