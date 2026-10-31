// node --test scripts/release/changelog.test.mjs   (bash scripts/verify.sh scripts runs every *.test.mjs)
import test from 'node:test'
import assert from 'node:assert/strict'
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

const BASE = 'https://github.com/o/r'
const CHANGELOG = `# Changelog

## [Unreleased]

### Added

- **F038 — Opening Position** — records a starting position.
  - Upgrade: a new migration adds a table.
- Seeds grew. (closes [#227](${BASE}/issues/227))

### Fixed

- Dialog keeps its draft. (closes [#232](${BASE}/issues/232)) ([#233](${BASE}/pull/233))

### Changed

- Reads are faster. (part of [#189](${BASE}/issues/189))
- Reads are faster still. (part of [#189](${BASE}/issues/189))

## [0.1.2] - 2026-10-21

### Added

- **F001 — Old** — already released.
`

test('parseUnreleased reads sections, keys, PR links and Upgrade lines, and stops at the next release', () => {
  const { found, sections } = parseUnreleased(CHANGELOG)
  assert.equal(found, true)
  assert.deepEqual(sections.map((s) => s.name), ['Added', 'Fixed', 'Changed'])
  const [f038, seeds] = sections[0].bullets
  assert.equal(f038.feature, 'F038')
  assert.equal(f038.upgrade, true)
  assert.deepEqual(seeds.issues, [227])
  assert.equal(seeds.upgrade, false)
  const dialog = sections[1].bullets[0]
  assert.deepEqual([dialog.issues, dialog.prs], [[232], [233]])
  assert.equal(sections.flatMap((s) => s.bullets).length, 5)
})

test('parseUnreleased reports a missing heading', () => {
  assert.equal(parseUnreleased('# Changelog\n').found, false)
})

test('sectionOrderViolations flags Fixed before Changed, and unknown sections', () => {
  const { sections } = parseUnreleased(CHANGELOG)
  assert.deepEqual(sectionOrderViolations(sections).misordered, [{ name: 'Changed', after: 'Fixed' }])
  assert.deepEqual(sectionOrderViolations([{ name: 'Added' }, { name: 'Changed' }, { name: 'Fixed' }]).misordered, [])
  assert.deepEqual(sectionOrderViolations([{ name: 'Added' }, { name: 'Misc' }]).unknown, ['Misc'])
})

test('parseCommit reads type, scope, PR, feature ids, closes, part of and breaking markers', () => {
  const c = parseCommit({
    hash: 'abc1234',
    subject: 'feat(F001,F014)!: do things (#12)',
    body: 'Closes #5\nPart of #9\n\nBREAKING CHANGE: x',
    files: ['backend/A.java'],
  })
  assert.equal(c.type, 'feat')
  assert.deepEqual(c.features, ['F001', 'F014'])
  assert.equal(c.pr, 12)
  assert.deepEqual([c.closes, c.partOf], [[5], [9]])
  assert.equal(c.breaking, true)
  assert.equal(c.docsOrToolingOnly, false)
  assert.equal(parseCommit({ hash: 'x', subject: 'Add PRD, README' }).type, null)
  assert.equal(classify(parseCommit({ hash: 'x', subject: 'Add PRD, README' })), 'unclassified')
})

test('parseCommit does not take the "Closes #N" placeholder as an issue', () => {
  assert.deepEqual(parseCommit({ hash: 'x', subject: 'docs: y (#1)', body: 'the floor (Closes #N, summary)' }).closes, [])
})

test('a docs or tooling only commit is flagged, a mixed one is not', () => {
  const only = parseCommit({ hash: 'a', subject: 'feat: x', files: ['.claude/skills/a.md', 'scripts/b.sh', 'docs/c.md'] })
  const mixed = parseCommit({ hash: 'b', subject: 'feat: x', files: ['docs/c.md', 'frontend/src/a.ts'] })
  assert.equal(only.docsOrToolingOnly, true)
  assert.equal(mixed.docsOrToolingOnly, false)
})

test('suggestBump follows ADR 0009 and says what tooling-only commits change', () => {
  const feat = parseCommit({ hash: 'a', subject: 'feat: x', files: ['scripts/a.sh'] })
  const fix = parseCommit({ hash: 'b', subject: 'fix: y', files: ['backend/a.java'] })
  const perf = parseCommit({ hash: 'c', subject: 'perf: z', files: ['backend/a.java'] })
  const chore = parseCommit({ hash: 'd', subject: 'chore: w' })
  assert.deepEqual(suggestBump([feat, fix, chore]), { bump: 'minor', withoutToolingOnly: 'patch' })
  assert.equal(suggestBump([perf, chore]).bump, 'none')
  assert.equal(suggestBump([fix]).bump, 'patch')
  assert.equal(suggestBump([parseCommit({ hash: 'e', subject: 'fix!: q' })]).bump, 'major')
})

test('bumpVersion', () => {
  assert.equal(bumpVersion('0.1.2', 'minor'), '0.2.0')
  assert.equal(bumpVersion('0.1.2', 'patch'), '0.1.3')
  assert.equal(bumpVersion('0.1.2', 'major'), '1.0.0')
  assert.equal(bumpVersion('0.1.2', 'none'), null)
})

const commit = (subject, extra = {}) => parseCommit({ hash: subject.slice(0, 6), subject, ...extra })

test('reconcile proposes the PR only for a commit that matches one bullet', () => {
  const { sections } = parseUnreleased(CHANGELOG)
  const bullets = sections.flatMap((s) => s.bullets)
  const result = reconcile(bullets, [
    commit('feat(F038): opening position (#235)'),
    commit('docs(F038): design (#234)'),
    commit('feat: seeds (#228)', { body: 'Closes #227' }),
    commit('fix: dialog (#233)', { body: 'Closes #232' }),
  ])
  const links = Object.fromEntries(result.missingLinks.map((m) => [m.bullet.title.slice(0, 8), m.proposed]))
  assert.deepEqual(links['**F038 —'], [234, 235])
  assert.deepEqual(links['Seeds gr'], [228])
  assert.equal(links['Dialog k'], undefined, 'a bullet that already links its PR is not proposed again')
})

test('reconcile reports a shared tracking issue as ambiguous instead of guessing', () => {
  const { sections } = parseUnreleased(CHANGELOG)
  const result = reconcile(sections.flatMap((s) => s.bullets), [commit('perf: faster (#220)', { body: 'Part of #189' })])
  assert.equal(result.ambiguous.length, 1)
  assert.equal(result.ambiguous[0].bullets.length, 2)
  assert.deepEqual(result.missingLinks.find((m) => m.bullet.title.startsWith('Reads are faster.')).proposed, [])
})

test('reconcile finds missing entries and stray bullets, and ignores types that need no entry', () => {
  const { sections } = parseUnreleased(CHANGELOG)
  const bullets = sections.flatMap((s) => s.bullets)
  const result = reconcile(bullets, [
    commit('feat(F099): new thing (#50)'),
    commit('chore: housekeeping (#51)'),
    commit('Add PRD, README'),
  ])
  assert.deepEqual(result.missingEntries.map((c) => c.pr), [50])
  assert.equal(result.strayBullets.length, bullets.length)
})

test('stableTags drops release candidates and sorts newest first', () => {
  assert.deepEqual(
    stableTags(['v0.1.2-rc.1', 'v0.1.2', 'v0.2.0-rc.1', 'v0.10.0', 'v0.2.0', 'latest', 'v0.1.1']),
    ['v0.10.0', 'v0.2.0', 'v0.1.2', 'v0.1.1'],
  )
})

test('version readers', () => {
  assert.equal(gradleVersion("group = 'a'\nversion = '0.2.0'\n"), '0.2.0')
  assert.equal(gradleVersion('no version here'), null)
  assert.equal(packageVersion('{"version": "0.2.0"}'), '0.2.0')
  assert.equal(packageVersion('not json'), null)
})

test('hasSkipMarker catches every spelling and nothing else', () => {
  for (const m of ['[skip ci]', '[ci skip]', '[no ci]', '[skip actions]', '[actions skip]', 'skip-checks: true']) {
    assert.equal(hasSkipMarker(`chore: x ${m}`), true, m)
  }
  assert.equal(hasSkipMarker('chore: skip the ci docs'), false)
})
