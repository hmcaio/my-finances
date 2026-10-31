// node --test scripts/bench/guard.test.mjs
import test from 'node:test'
import assert from 'node:assert/strict'
import { violations } from './guard.mjs'

const ctx = { project: 'mf-bench-1', tag: 'bench-1', port: '18081', scratch: '/scratch/stack' }
const good = () => ({
  services: {
    postgres: { image: 'postgres:18.6-alpine', volumes: [{ type: 'volume', source: 'data' }] },
    backup: {
      image: 'ghcr.io/hmcaio/my-finances-backup:bench-1',
      volumes: [{ type: 'bind', source: '/scratch/stack/backups' }],
    },
    backend: {
      image: 'ghcr.io/hmcaio/my-finances-backend:bench-1',
      volumes: [{ type: 'volume', source: 'logs' }],
    },
    frontend: {
      image: 'ghcr.io/hmcaio/my-finances-frontend:bench-1',
      ports: [{ host_ip: '127.0.0.1', published: '18081', target: 80 }],
    },
  },
  volumes: { data: { name: 'mf-bench-1_data' }, logs: { name: 'mf-bench-1_logs' } },
  networks: { default: { name: 'mf-bench-1_default' } },
})

test('a project-scoped stack passes', () => assert.deepEqual(violations(good(), ctx), []))

test('names built from the project through STACK_NAME pass', () => {
  const config = good()
  config.services.backend.container_name = 'mf-bench-1-backend-prod'
  config.volumes.logs = { name: 'mf-bench-1-logs-prod' }
  assert.deepEqual(violations(config, ctx), [])
})

test('a pinned container name is refused', () => {
  const config = good()
  config.services.backend.container_name = 'my-finances-backend-prod'
  assert.match(violations(config, ctx).join('\n'), /pins container_name/)
})

test('the pinned prod logs volume is refused (a down -v would delete the real logs)', () => {
  const config = good()
  config.volumes.logs = { name: 'my-finances-logs-prod' }
  assert.match(violations(config, ctx).join('\n'), /not scoped to project/)
})

test('the external backups volume is refused, mounted or not', () => {
  const config = good()
  config.volumes.backups = { name: 'my-finances-backups-prod', external: true }
  assert.match(violations(config, ctx).join('\n'), /external/)
})

test('a published port other than the frontend on loopback is refused', () => {
  for (const port of [
    { service: 'frontend', host_ip: '0.0.0.0', published: '18081' },
    { service: 'frontend', host_ip: '127.0.0.1', published: '8081' },
    { service: 'backend', host_ip: '127.0.0.1', published: '18081' },
  ]) {
    const config = good()
    config.services[port.service].ports = [
      { host_ip: port.host_ip, published: port.published, target: 80 },
    ]
    assert.notDeepEqual(violations(config, ctx), [], JSON.stringify(port))
  }
})

test('an image of ours without the benchmark tag is refused', () => {
  const config = good()
  config.services.backend.image = 'ghcr.io/hmcaio/my-finances-backend:v0.1.2'
  assert.match(violations(config, ctx).join('\n'), /benchmark tag/)
})

test('a bind mount outside the scratch directory, and a shared network, are refused', () => {
  const config = good()
  config.services.backup.volumes = [{ type: 'bind', source: '/var/run/docker.sock' }]
  config.networks.default = { name: 'my-finances_default' }
  const text = violations(config, ctx).join('\n')
  assert.match(text, /outside the scratch directory/)
  assert.match(text, /network default/)
})
