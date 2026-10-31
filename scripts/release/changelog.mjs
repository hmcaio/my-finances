// Pure parsing and matching for scripts/release/reconcile.mjs (the release skill's Steps 1-5): no git,
// no gh, no filesystem, so scripts/release/changelog.test.mjs covers it with plain strings.

export const SECTION_ORDER = ['Added', 'Changed', 'Deprecated', 'Removed', 'Fixed', 'Security']

const SKIP_MARKER = /\[(skip ci|ci skip|no ci|skip actions|actions skip)\]|skip-checks:\s*true/i
export const hasSkipMarker = (text) => SKIP_MARKER.test(text)

/**
 * The `## [Unreleased]` section of a CHANGELOG as sections of top-level bullets. A bullet's keys
 * are the feature id of a leading `**FXXX — Title**`, the issues it cites (`/issues/N` links) and
 * the pull requests it already links (`/pull/N` links); a nested `- Upgrade...` line marks it.
 */
export function parseUnreleased(markdown) {
  const lines = markdown.split('\n')
  const start = lines.findIndex((l) => l.trim() === '## [Unreleased]')
  if (start === -1) return { found: false, sections: [] }
  const sections = []
  let section = null
  let bullet = null
  for (const line of lines.slice(start + 1)) {
    if (/^## \[/.test(line)) break
    const heading = line.match(/^### (.+?)\s*$/)
    if (heading) {
      section = { name: heading[1], bullets: [] }
      sections.push(section)
      bullet = null
      continue
    }
    if (!section) continue
    if (line.startsWith('- ')) {
      bullet = bulletOf(line, section.name)
      section.bullets.push(bullet)
    } else if (bullet && /^\s+-\s+Upgrade\b/.test(line)) {
      bullet.upgrade = true
    }
  }
  return { found: true, sections }
}

function bulletOf(line, section) {
  const feature = line.match(/^- \*\*(F\d{3})\b/)
  const numbers = (kind) =>
    [...line.matchAll(new RegExp(`/${kind}/(\\d+)\\)`, 'g'))].map((m) => Number(m[1]))
  // A bare `closes #N` / `part of #N` without a link counts as an issue reference too.
  const bare = [...line.matchAll(/\b(?:closes|part of)\s+#(\d+)/gi)].map((m) => Number(m[1]))
  return {
    section,
    text: line,
    title: line.replace(/^- /, '').slice(0, 80),
    feature: feature ? feature[1] : null,
    issues: [...new Set([...numbers('issues'), ...bare])],
    prs: [...new Set(numbers('pull'))],
    upgrade: false,
  }
}

/** Sections out of the Keep a Changelog order of docs/conventions/release.md: [{name, after}]. */
export function sectionOrderViolations(sections) {
  const rank = (name) => SECTION_ORDER.indexOf(name)
  const known = sections.filter((s) => rank(s.name) !== -1)
  const out = []
  for (let i = 1; i < known.length; i++) {
    if (rank(known[i].name) < rank(known[i - 1].name)) {
      out.push({ name: known[i].name, after: known[i - 1].name })
    }
  }
  const unknown = sections.filter((s) => rank(s.name) === -1).map((s) => s.name)
  return { misordered: out, unknown }
}

/**
 * One first-parent commit as the release skill reads it. `body` is the full message after the
 * subject; `closing` are issues GitHub links to the PR (from `gh`), merged into `closes`.
 */
export function parseCommit({ hash, subject, body = '', files = [], closing = [] }) {
  const m = subject.match(/^(\w+)(?:\(([^)]*)\))?(!)?:\s*(.*?)(?:\s+\(#(\d+)\))?$/)
  const scope = m && m[2] ? m[2] : ''
  const refs = (re) => [...body.matchAll(re)].map((x) => Number(x[1]))
  const closes = [...new Set([...refs(/\b(?:closes|fixes|resolves)\s+#(\d+)/gi), ...closing])]
  return {
    hash,
    subject,
    type: m ? m[1] : null,
    scope,
    breaking: Boolean((m && m[3]) || /^BREAKING CHANGE:/m.test(body)),
    pr: m && m[5] ? Number(m[5]) : null,
    features: [...scope.matchAll(/F\d{3}/g)].map((x) => x[0]),
    closes,
    partOf: refs(/\bpart of\s+#(\d+)/gi),
    files,
    docsOrToolingOnly: files.length > 0 && files.every(isDocsOrTooling),
  }
}

// Paths whose change no user of the running app sees (the skill's "tooling or docs" test).
export const isDocsOrTooling = (file) =>
  /^(docs|\.claude|\.github|scripts)\//.test(file) ||
  /(^|\/)[^/]*\.md$/.test(file) ||
  file === '.gitignore' ||
  file === 'CLAUDE.md'

const CANDIDATE_TYPES = new Set(['feat', 'fix', 'perf', 'refactor'])

/** ADR 0009 / the skill's table: which commits could need an entry, and the bump they imply. */
export function classify(commit) {
  if (commit.type === null) return 'unclassified'
  if (commit.breaking) return 'breaking'
  return CANDIDATE_TYPES.has(commit.type) ? 'candidate' : 'other'
}

export function suggestBump(commits) {
  const bump = (list) => {
    if (list.some((c) => c.breaking)) return 'major'
    if (list.some((c) => c.type === 'feat')) return 'minor'
    if (list.some((c) => c.type === 'fix')) return 'patch'
    return 'none'
  }
  const visible = commits.filter((c) => classify(c) !== 'other' && classify(c) !== 'unclassified')
  return {
    bump: bump(visible),
    // The same bump without feat/fix commits that touch only docs or tooling (the skill's caveat).
    withoutToolingOnly: bump(visible.filter((c) => !c.docsOrToolingOnly)),
  }
}

/**
 * Which bullet covers which commit. A commit matches a bullet through the feature id in its scope,
 * an issue it closes or is part of, or a PR the bullet already links. A PR is proposed for a bullet
 * only when that commit matches exactly this bullet; a commit that matches several (one tracking
 * issue shared by several bullets) is reported as ambiguous for the agent to place.
 */
export function reconcile(bullets, commits) {
  const keys = (c) => ({ features: c.features, issues: [...c.closes, ...c.partOf], pr: c.pr })
  const matches = (b, c) => {
    const k = keys(c)
    return (
      (b.feature && k.features.includes(b.feature)) ||
      k.issues.some((n) => b.issues.includes(n)) ||
      (k.pr !== null && b.prs.includes(k.pr))
    )
  }
  const byCommit = new Map(commits.map((c) => [c.hash, bullets.filter((b) => matches(b, c))]))
  const proposals = new Map(bullets.map((b) => [b, []]))
  const ambiguous = []
  for (const c of commits) {
    const hit = byCommit.get(c.hash)
    if (c.pr === null) continue
    if (hit.length === 1) {
      if (!hit[0].prs.includes(c.pr)) proposals.get(hit[0]).push(c.pr)
    } else if (hit.length > 1 && hit.some((b) => !b.prs.includes(c.pr))) {
      ambiguous.push({ commit: c, bullets: hit })
    }
  }
  const covered = (c) => byCommit.get(c.hash).length > 0
  return {
    missingEntries: commits.filter((c) => ['candidate', 'breaking'].includes(classify(c)) && !covered(c)),
    strayBullets: bullets.filter((b) => !commits.some((c) => matches(b, c))),
    missingLinks: bullets
      .filter((b) => b.prs.length === 0)
      .map((b) => ({ bullet: b, proposed: [...new Set(proposals.get(b))].sort((a, z) => a - z) })),
    ambiguous,
  }
}

/** `version = '1.2.3'` of backend/build.gradle, or the "version" field of a package.json text. */
export const gradleVersion = (text) => (text.match(/^version\s*=\s*'([^']+)'/m) || [])[1] || null
export const packageVersion = (text) => {
  try {
    return JSON.parse(text).version || null
  } catch {
    return null
  }
}

export function bumpVersion(last, bump) {
  const [major, minor, patch] = last.split('.').map(Number)
  if (bump === 'major') return `${major + 1}.0.0`
  if (bump === 'minor') return `${major}.${minor + 1}.0`
  if (bump === 'patch') return `${major}.${minor}.${patch + 1}`
  return null
}

/**
 * Stable release tags, newest first. A glob like `v[0-9]*.[0-9]*.[0-9]*` also matches
 * `v1.2.3-rc.1` (its last `*` takes the suffix), so the filter is exact.
 */
export const stableTags = (tags) =>
  tags
    .filter((t) => /^v\d+\.\d+\.\d+$/.test(t))
    .sort((a, b) => {
      const [x, y] = [a, b].map((t) => t.slice(1).split('.').map(Number))
      return y[0] - x[0] || y[1] - x[1] || y[2] - x[2]
    })
