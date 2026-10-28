// Pre-flight for the prod-shaped benchmark stack: reads the RESOLVED compose config
// (`docker compose ... config --format json`) and exits 1 unless the stack can only touch
// resources of its own, so it can run beside a real prod stack. run.sh runs it before anything is
// created. It checks the resolved config, not the override, so a name newly pinned in
// docker-compose.prod.yml fails the run instead of reaching prod.
//
//   node guard.mjs <config.json> <project> <image tag> <frontend port> <scratch dir>
import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

const OUR_IMAGES = 'ghcr.io/hmcaio/'

export function violations(config, { project, tag, port, scratch }) {
  const found = []
  const scoped = (name) => typeof name === 'string' && name.startsWith(`${project}_`)

  for (const [service, def] of Object.entries(config.services ?? {})) {
    if (def.container_name) found.push(`${service}: pins container_name '${def.container_name}'`)
    if (def.network_mode) found.push(`${service}: network_mode '${def.network_mode}'`)
    if (def.privileged) found.push(`${service}: privileged`)
    if (def.pid || def.ipc) found.push(`${service}: shares the host's pid/ipc namespace`)
    if (def.image?.startsWith(OUR_IMAGES) && !def.image.endsWith(`:${tag}`)) {
      found.push(`${service}: image '${def.image}' does not carry the benchmark tag '${tag}'`)
    }
    for (const mount of def.volumes ?? []) {
      if (mount.type === 'volume' && mount.source) {
        const volume = config.volumes?.[mount.source]
        if (!volume) found.push(`${service}: mounts undeclared volume '${mount.source}'`)
      }
      if (mount.type === 'bind' && !String(mount.source).startsWith(scratch)) {
        found.push(`${service}: bind-mounts '${mount.source}' outside the scratch directory`)
      }
    }
    for (const published of def.ports ?? []) {
      const ok =
        service === 'frontend' &&
        published.host_ip === '127.0.0.1' &&
        String(published.published) === String(port)
      if (!ok) {
        found.push(
          `${service}: publishes ${published.host_ip ?? '*'}:${published.published}->${published.target}, only the frontend on 127.0.0.1:${port} is allowed`,
        )
      }
    }
  }
  for (const [key, volume] of Object.entries(config.volumes ?? {})) {
    if (volume.external) found.push(`volume ${key}: external ('${volume.name}')`)
    else if (!scoped(volume.name)) {
      found.push(`volume ${key}: named '${volume.name}', not scoped to project '${project}'`)
    }
  }
  for (const [key, network] of Object.entries(config.networks ?? {})) {
    if (network.external) found.push(`network ${key}: external ('${network.name}')`)
    else if (!scoped(network.name)) {
      found.push(`network ${key}: named '${network.name}', not scoped to project '${project}'`)
    }
  }
  return found
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [file, project, tag, port, scratch] = process.argv.slice(2)
  if (!scratch) throw new Error('usage: guard.mjs <config.json> <project> <tag> <port> <scratch>')
  const found = violations(JSON.parse(readFileSync(file, 'utf8')), { project, tag, port, scratch })
  if (found.length) {
    console.error('bench: the stack would not be isolated from a real prod stack:')
    for (const line of found) console.error(`  - ${line}`)
    process.exit(1)
  }
  console.log(
    `bench: guard ok (project ${project}: names, volumes, network, port and images are its own)`,
  )
}
