// node --test scripts/stack/isolation.test.mjs   (bash scripts/verify.sh scripts runs every *.test.mjs)
// The rendering tests need `docker compose` (always present on a CI runner); the rule tests need nothing.
import test from 'node:test'
import assert from 'node:assert/strict'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { REAL, composeAvailable, defaultViolations, isolatedViolations, render } from './isolation.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const DEV = join(ROOT, 'docker-compose.yml')
const PROD = join(ROOT, 'docker-compose.prod.yml')

const compose = composeAvailable()
const skip = !compose && !process.env.GITHUB_ACTIONS && 'docker compose is not installed'

const PORTS = { postgres: 15432, pgadmin: 15050, backend: 18080, frontend: 15173 }
const DEV_ISO = { STACK_NAME: 'iso', POSTGRES_PORT: PORTS.postgres, PGADMIN_PORT: PORTS.pgadmin, BACKEND_PORT: PORTS.backend, FRONTEND_DEV_PORT: PORTS.frontend }
const PROD_ISO = { STACK_NAME: 'iso', FRONTEND_PORT: 18081 }

// --- the rules, on hand-made configs -------------------------------------------------------------

const devConfig = (stack, ports) => ({
  name: `${stack}-dev`,
  services: {
    postgres: { container_name: `${stack}-postgres`, ports: [{ published: String(ports.postgres) }] },
    pgadmin: { container_name: `${stack}-pgadmin`, ports: [{ published: String(ports.pgadmin) }] },
    backend: {
      container_name: `${stack}-backend`,
      ports: [{ published: String(ports.backend) }],
      environment: { MYFINANCES_CORS_ALLOWED_ORIGINS: `http://localhost:${ports.frontend},http://127.0.0.1:${ports.frontend}` },
    },
    frontend: {
      container_name: `${stack}-frontend`,
      ports: [{ published: String(ports.frontend) }],
      environment: { VITE_API_BASE_URL: `http://localhost:${ports.backend}/api` },
    },
  },
  volumes: { data: { name: `${stack}-dev_data` } },
})

test('the real dev stack passes the default rules and an own stack passes the isolated ones', () => {
  assert.deepEqual(defaultViolations('dev', devConfig('my-finances', REAL.dev.ports)), [])
  assert.deepEqual(isolatedViolations('dev', devConfig('iso', PORTS), { stack: 'iso', ports: PORTS }), [])
})

test('a dev stack that kept a real name, a real port or the real API port is refused', () => {
  const config = devConfig('iso', PORTS)
  config.services.backend.container_name = 'my-finances-backend'
  config.services.postgres.ports = [{ published: '5432' }]
  config.services.frontend.environment.VITE_API_BASE_URL = 'http://localhost:8080/api'
  const found = isolatedViolations('dev', config, { stack: 'iso', ports: PORTS }).join('\n')
  assert.match(found, /backend: container_name 'my-finances-backend' is not under 'iso-'/)
  assert.match(found, /postgres: publishes \[5432\]/)
  assert.match(found, /VITE_API_BASE_URL/)
})

test('a prod stack that kept the real logs or backups volume is refused', () => {
  const config = {
    services: Object.fromEntries(Object.keys(REAL.prod.containers).map((s) => [s, { container_name: `iso-${s}-prod` }])),
    volumes: { 'my-finances-logs-prod': { name: 'my-finances-logs-prod' }, 'my-finances-backups-prod': { name: 'my-finances-backups-prod', external: true } },
  }
  config.services.frontend.ports = [{ published: '18081' }]
  const found = isolatedViolations('prod', config, { stack: 'iso', frontendPort: 18081 }).join('\n')
  assert.match(found, /volume my-finances-logs-prod: named 'my-finances-logs-prod'/)
  assert.match(found, /volume my-finances-backups-prod: named 'my-finances-backups-prod'/)
})

test('a missing service is reported instead of passing silently', () => {
  assert.match(isolatedViolations('prod', { services: {}, volumes: {} }, { stack: 'iso', frontendPort: 1 }).join('\n'), /backend: missing/)
})

// --- the real files -------------------------------------------------------------------------------

test('CI has docker compose, so the rendering tests cannot be skipped there', { skip: !process.env.GITHUB_ACTIONS }, () => {
  assert.equal(compose, true)
})

test('dev renders exactly the real stack when no variable is set', { skip }, () => {
  assert.deepEqual(defaultViolations('dev', render(DEV)), [])
})

test('prod renders exactly the real stack when no variable is set', { skip }, () => {
  assert.deepEqual(defaultViolations('prod', render(PROD)), [])
})

test('dev shares no name or port with the real stack under STACK_NAME and four ports', { skip }, () => {
  assert.deepEqual(isolatedViolations('dev', render(DEV, DEV_ISO), { stack: 'iso', ports: PORTS }), [])
})

test('prod shares no name or volume with the real stack under STACK_NAME', { skip }, () => {
  assert.deepEqual(isolatedViolations('prod', render(PROD, PROD_ISO), { stack: 'iso', frontendPort: 18081 }), [])
})

test('the isolated rules do reject the real stack (they can fail)', { skip }, () => {
  assert.notDeepEqual(isolatedViolations('dev', render(DEV), { stack: 'iso', ports: PORTS }), [])
  assert.notDeepEqual(isolatedViolations('prod', render(PROD), { stack: 'iso', frontendPort: 18081 }), [])
})
