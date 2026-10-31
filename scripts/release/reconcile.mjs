#!/usr/bin/env node
// Steps 1-5 of the release skill (.claude/skills/release/SKILL.md) as one read-only report: the
// source ref, the last release and its range, the commits classified by ADR 0009, the
// `## [Unreleased]` bullets matched against them, the upgrade-sensitive and rc-sensitive diffs,
// skip-CI markers and the version fields. It decides nothing that needs judgement (a borderline
// commit, a missing bullet's wording, whether an `Upgrade:` note is enough): it lists the facts.
//
//   node scripts/release/reconcile.mjs [--ref origin/main] [--since vX.Y.Z] [--no-fetch] [--no-gh] [--json]
//
//   --ref      the commit to release (default origin/main); the CHANGELOG is read from it, not from
//              the working tree
//   --no-fetch skip `git fetch origin --tags`
//   --no-gh    do not ask `gh` which issues a PR closes (needed for a fix whose message has no
//              `Closes #N`); uses only what the commit messages say
//   --since    treat this release tag as the last one (replay an older range, e.g. --since v0.1.2)
//   --json     print the data instead of the report
//
// Needs git, and gh unless --no-gh. Writes nothing, anywhere.
import { execFileSync } from 'node:child_process'
import {
  bumpVersion,
  classify,
  gradleVersion,
  hasSkipMarker,
  packageVersion,
  parseCommit,
  parseUnreleased,
  reconcile,
  sectionOrderViolations,
  stableTags,
  suggestBump,
} from './changelog.mjs'

const args = process.argv.slice(2)
const flag = (name) => args.includes(name)
const refIndex = args.indexOf('--ref')
const ref = refIndex === -1 ? 'origin/main' : args[refIndex + 1]

const git = (...a) => execFileSync('git', a, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).trimEnd()
const tryGit = (...a) => {
  try {
    return git(...a)
  } catch {
    return null
  }
}
const warnings = []

if (!flag('--no-fetch')) {
  if (tryGit('fetch', 'origin', '--tags', '--quiet') === null) warnings.push('git fetch failed; working from local refs')
}

const refSha = tryGit('rev-parse', '--short', ref)
if (refSha === null) {
  console.error(`reconcile: cannot resolve ${ref}`)
  process.exit(2)
}
const localMain = tryGit('rev-parse', '--short', 'main')
if (ref === 'origin/main' && localMain && localMain !== refSha) {
  warnings.push(`local main (${localMain}) differs from origin/main (${refSha}); using origin/main`)
}

const tags = stableTags(git('tag', '-l', 'v*').split('\n'))
const sinceIndex = args.indexOf('--since')
const lastTag = sinceIndex === -1 ? tags[0] || null : args[sinceIndex + 1]
if (lastTag && git('log', `${ref}..${lastTag}`, '--oneline')) {
  warnings.push(`${lastTag} is not on ${ref}: the CI tag guard refuses to publish such a tag`)
}
const range = lastTag ? `${lastTag}..${ref}` : ref

const records = git('log', range, '--first-parent', '--format=%H%x1f%s%x1f%b%x1e')
  .split('\x1e')
  .map((r) => r.replace(/^\n/, ''))
  .filter(Boolean)
const commits = records.map((r) => {
  const [hash, subject, body] = r.split('\x1f')
  const files = git('diff-tree', '--no-commit-id', '--name-only', '-r', hash).split('\n').filter(Boolean)
  return parseCommit({ hash: hash.slice(0, 7), subject, body, files })
})

// A fix often says nothing about its issue in the commit message; GitHub knows which issues the PR
// closes.
if (!flag('--no-gh')) {
  let ghFailed = false
  for (const c of commits) {
    const unkeyed = c.pr !== null && !c.closes.length && !c.partOf.length && !c.features.length
    if (!unkeyed || !['candidate', 'breaking'].includes(classify(c)) || ghFailed) continue
    try {
      const out = execFileSync(
        'gh',
        ['pr', 'view', String(c.pr), '--json', 'closingIssuesReferences', '-q', '[.closingIssuesReferences[].number]|join(" ")'],
        { encoding: 'utf8' },
      ).trim()
      c.closes = out ? out.split(' ').map(Number) : []
    } catch {
      ghFailed = true
      warnings.push('gh could not read a PR; commits without Closes/Part of may show as unmatched')
    }
  }
}

const changelog = tryGit('show', `${ref}:CHANGELOG.md`)
const unreleased = changelog === null ? { found: false, sections: [] } : parseUnreleased(changelog)
const bullets = unreleased.sections.flatMap((s) => s.bullets)
const result = reconcile(bullets, commits)
const order = sectionOrderViolations(unreleased.sections)

const upgradePaths = [
  'docker-compose*.yml',
  '.env.example',
  'backend/src/main/resources/application*.yml',
  'backend/src/main/resources/db/migration',
  '**/Dockerfile',
  'frontend/nginx.conf',
  '.github/workflows',
]
const rcPaths = ['.github/workflows', '**/Dockerfile', 'docker-compose*.yml']
const diffStat = (paths) =>
  lastTag ? git('diff', '--stat', lastTag, ref, '--', ...paths) : '(first release: nothing to diff against)'
const rcFiles = lastTag
  ? git('diff', '--name-only', lastTag, ref, '--', ...rcPaths).split('\n').filter(Boolean)
  : []

const bump = suggestBump(commits)
const backend = gradleVersion(tryGit('show', `${ref}:backend/build.gradle`) || '')
const frontend = packageVersion(tryGit('show', `${ref}:frontend/package.json`) || '')
const nextVersion = lastTag ? bumpVersion(lastTag.slice(1), bump.bump) : '0.1.0'
if (backend !== frontend) warnings.push(`backend (${backend}) and frontend (${frontend}) versions differ (ADR 0007 requires them equal)`)
if (bump.bump === 'major' && lastTag && lastTag.startsWith('v0.')) {
  warnings.push('breaking change while 0.x: ADR 0009 says MAJOR, ADR 0007 reserves 1.0.0 for "stable end to end"; the user chooses')
}
if (changelog === null) warnings.push(`CHANGELOG.md not found at ${ref}`)
else if (!unreleased.found) warnings.push('no "## [Unreleased]" heading in the CHANGELOG')
if (order.unknown.length) warnings.push(`unknown CHANGELOG sections in [Unreleased]: ${order.unknown.join(', ')}`)

const skipRange = commits.filter((c) => hasSkipMarker(git('show', '-s', '--format=%B', c.hash)))
const tipSkip = hasSkipMarker(git('show', '-s', '--format=%B', ref))

const data = {
  ref, refSha, lastTag, range, nextVersion, bump, versions: { backend, frontend },
  commits: commits.map((c) => ({ ...c, class: classify(c) })),
  bullets: bullets.map((b) => ({ section: b.section, title: b.title, feature: b.feature, issues: b.issues, prs: b.prs, upgrade: b.upgrade })),
  order, missingEntries: result.missingEntries.map((c) => c.hash),
  strayBullets: result.strayBullets.map((b) => b.title),
  missingLinks: result.missingLinks.map((m) => ({ bullet: m.bullet.title, proposed: m.proposed })),
  ambiguous: result.ambiguous.map((a) => ({ commit: a.commit.hash, bullets: a.bullets.map((b) => b.title) })),
  rc: { recommended: !lastTag || rcFiles.length > 0, files: rcFiles },
  skipMarkers: { tip: tipSkip, range: skipRange.map((c) => c.hash) }, warnings,
}
if (flag('--json')) {
  console.log(JSON.stringify(data, null, 2))
  process.exit(0)
}

const out = []
const h = (t) => out.push('', `## ${t}`)
const pr = (n) => `#${n}`
out.push(`Release reconcile: ${ref} (${refSha})`)
out.push(`Last release: ${lastTag || 'none (first release)'}   range: ${range}   commits: ${commits.length}`)
const byType = {}
for (const c of commits) byType[c.type || '(none)'] = (byType[c.type || '(none)'] || 0) + 1
out.push(`By type: ${Object.entries(byType).map(([t, n]) => `${n} ${t}`).join(', ')}`)
out.push(`Versions: backend ${backend}, frontend ${frontend}`)
out.push(
  `Suggested bump: ${bump.bump}${nextVersion ? ` -> ${nextVersion}` : ''}` +
    (bump.withoutToolingOnly !== bump.bump ? `   (without docs/tooling-only feat/fix: ${bump.withoutToolingOnly})` : ''),
)

h('Commits (hash class type(scope) #PR keys flags)')
for (const c of commits) {
  const keys = [...c.features, ...c.closes.map((n) => `closes #${n}`), ...c.partOf.map((n) => `part of #${n}`)]
  const flags = [c.breaking && 'BREAKING', c.docsOrToolingOnly && 'docs/tooling-only'].filter(Boolean)
  out.push(
    `${c.hash} ${classify(c).padEnd(12)} ${(c.type || '?') + (c.scope ? `(${c.scope})` : '')}${c.pr ? ` ${pr(c.pr)}` : ''}` +
      `${keys.length ? `  [${keys.join('; ')}]` : ''}${flags.length ? `  {${flags.join(', ')}}` : ''}  ${c.subject.slice(0, 70)}`,
  )
}

h(`[Unreleased]: ${bullets.length} bullets in ${unreleased.sections.map((s) => s.name).join(', ') || 'no sections'}`)
for (const b of bullets) {
  const keys = [b.feature, ...b.issues.map((n) => `closes ${pr(n)}`), ...b.prs.map((n) => `pr ${pr(n)}`)].filter(Boolean)
  out.push(`${b.section.padEnd(8)} ${b.upgrade ? 'U' : ' '} [${keys.join('; ') || 'no key'}]  ${b.title}`)
}

h('Findings')
const list = (title, items, none = 'none') => {
  out.push(`${title}: ${items.length ? '' : none}`)
  for (const i of items) out.push(`  - ${i}`)
}
list('a. Missing entry (candidate commit matching no bullet)',
  result.missingEntries.map((c) => `${c.hash} ${c.subject.slice(0, 80)}${c.docsOrToolingOnly ? '  {docs/tooling-only: likely no entry}' : ''}`))
list('b. Missing PR link (proposed from commits matching only that bullet)',
  result.missingLinks.map((m) => `${m.bullet.title}  ->  ${m.proposed.length ? m.proposed.map(pr).join(', ') : 'no proposal'}`))
if (result.ambiguous.length) {
  out.push('   Ambiguous (one commit matches several bullets, place it by judgement):')
  for (const a of result.ambiguous) out.push(`   - ${pr(a.commit.pr)} ${a.commit.hash} -> ${a.bullets.map((b) => b.title.slice(0, 40)).join(' | ')}`)
}
list('c. Stray bullet (matches no commit in the range)', result.strayBullets.map((b) => b.title))
list('d. Section mismatch', ['(by judgement: compare each bullet\'s section with its commit type in the table above)'])
list('f. Entry that may not belong (its only matching commits are docs/tooling-only)',
  bullets.filter((b) => {
    const hits = commits.filter((c) => (b.feature && c.features.includes(b.feature)) || [...c.closes, ...c.partOf].some((n) => b.issues.includes(n)))
    return hits.length > 0 && hits.every((c) => c.docsOrToolingOnly && !['feat', 'fix'].includes(c.type))
  }).map((b) => b.title))
list('h. Section order (Added, Changed, Deprecated, Removed, Fixed, Security)',
  order.misordered.map((o) => `${o.name} comes after ${o.after}; reorder`))

h('e. Upgrade-sensitive diff since the last release (check each change has an Upgrade: line)')
out.push(diffStat(upgradePaths) || '(no change)')
out.push(`Bullets with an Upgrade: line: ${bullets.filter((b) => b.upgrade).length}`)
if (!lastTag) out.push('First release: list migrations, .env.example keys, compose volumes and services as they stand.')

h('g. Release candidate')
out.push(
  !lastTag
    ? 'Recommended: no earlier release.'
    : rcFiles.length
      ? `Recommended: changed since ${lastTag}: ${rcFiles.join(', ')}`
      : 'Not recommended (optional): no workflow, Dockerfile or compose change since the last release.',
)

h('Skip-CI markers')
out.push(`tip of ${ref}: ${tipSkip ? 'MARKER FOUND' : 'none'}   in range: ${skipRange.length ? skipRange.map((c) => c.hash).join(', ') : 'none'}`)

if (warnings.length) {
  h('Warnings')
  for (const w of warnings) out.push(`- ${w}`)
}
console.log(out.join('\n'))
