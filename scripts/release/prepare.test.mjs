// node --test scripts/release/prepare.test.mjs   (bash scripts/verify.sh scripts runs every *.test.mjs)
// prepare.sh against a throwaway repository with a bare origin: nothing of this repo is touched.
import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), 'prepare.sh')
const ENV = {
  ...process.env,
  GIT_AUTHOR_NAME: 't', GIT_AUTHOR_EMAIL: 't@t', GIT_COMMITTER_NAME: 't', GIT_COMMITTER_EMAIL: 't@t',
  GIT_CONFIG_GLOBAL: '/dev/null', GIT_CONFIG_SYSTEM: '/dev/null',
}
const git = (cwd, ...args) => execFileSync('git', args, { cwd, env: ENV, encoding: 'utf8' }).trim()
const lock = (v) => JSON.stringify({ name: 'x', version: v, lockfileVersion: 3, requires: true, packages: { '': { name: 'x', version: v } } }, null, 2)

/** A clone of a bare origin holding the files prepare.sh touches, at the given frontend version. */
function fixture({ frontend = '0.1.2', verify = 'exit 0' } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'prepare-test-'))
  const origin = join(dir, 'origin.git')
  const repo = join(dir, 'repo')
  git(dir, 'init', '-q', '--bare', '-b', 'main', origin)
  git(dir, 'clone', '-q', origin, repo)
  for (const d of ['backend', 'frontend', 'scripts/release']) mkdirSync(join(repo, d), { recursive: true })
  writeFileSync(join(repo, 'backend/build.gradle'), "group = 'a'\nversion = '0.1.2'\n")
  writeFileSync(join(repo, 'frontend/package.json'), JSON.stringify({ name: 'x', version: frontend }, null, 2))
  writeFileSync(join(repo, 'frontend/package-lock.json'), lock(frontend))
  writeFileSync(join(repo, 'CHANGELOG.md'), '# Changelog\n\n## [Unreleased]\n\n- a thing\n')
  writeFileSync(join(repo, 'scripts/verify.sh'), `#!/usr/bin/env bash\n${verify}\n`)
  copyFileSync(SCRIPT, join(repo, 'scripts/release/prepare.sh'))
  git(repo, 'add', '-A')
  git(repo, 'commit', '-q', '-m', 'init')
  git(repo, 'push', '-q', 'origin', 'HEAD:main')
  git(repo, 'fetch', '-q', 'origin')
  const proposed = join(dir, 'CHANGELOG.proposed.md')
  writeFileSync(proposed, '# Changelog\n\n## [Unreleased]\n\n## [0.2.0] - 2026-10-30\n\n- a thing ([#1](u))\n')
  return { dir, repo, proposed, base: git(repo, 'rev-parse', 'origin/main') }
}

const run = (repo, ...args) =>
  spawnSync('bash', [join(repo, 'scripts/release/prepare.sh'), ...args], { cwd: repo, env: ENV, encoding: 'utf8' })

test('makes one commit with the four release files on a branch in a worktree under .claude/worktrees', () => {
  const { repo, proposed, base } = fixture()
  const r = run(repo, '0.2.0', proposed, '--base', base, '--skip-verify')
  assert.equal(r.status, 0, r.stderr)
  const wt = join(repo, '.claude/worktrees/release-v0.2.0')
  assert.equal(git(wt, 'branch', '--show-current'), 'chore/release-v0.2.0')
  assert.equal(git(wt, 'log', '-1', '--format=%s'), 'chore: release v0.2.0')
  assert.equal(git(wt, 'rev-list', '--count', 'origin/main..HEAD'), '1')
  assert.deepEqual(
    git(wt, 'show', '--name-only', '--format=', 'HEAD').split('\n').sort(),
    ['CHANGELOG.md', 'backend/build.gradle', 'frontend/package-lock.json', 'frontend/package.json'],
  )
  assert.equal(git(wt, 'status', '--short'), '')
  assert.match(readFileSync(join(wt, 'backend/build.gradle'), 'utf8'), /^version = '0\.2\.0'$/m)
  assert.equal(JSON.parse(readFileSync(join(wt, 'frontend/package.json'), 'utf8')).version, '0.2.0')
  assert.equal(JSON.parse(readFileSync(join(wt, 'frontend/package-lock.json'), 'utf8')).version, '0.2.0')
  assert.equal(readFileSync(join(wt, 'CHANGELOG.md'), 'utf8'), readFileSync(proposed, 'utf8'))
  assert.equal(spawnSync('git', ['config', 'branch.chore/release-v0.2.0.remote'], { cwd: repo, env: ENV }).stdout.length, 0, 'no upstream until pushed')
  assert.equal(git(repo, 'ls-remote', '--heads', 'origin', 'chore/release-v0.2.0'), '', 'nothing is pushed')
})

test('commits fewer files when a version already matches the target', () => {
  const { repo, proposed } = fixture({ frontend: '0.2.0' })
  const r = run(repo, '0.2.0', proposed, '--skip-verify')
  assert.equal(r.status, 0, r.stderr)
  const wt = join(repo, '.claude/worktrees/release-v0.2.0')
  assert.deepEqual(git(wt, 'show', '--name-only', '--format=', 'HEAD').split('\n').sort(), ['CHANGELOG.md', 'backend/build.gradle'])
})

test('refuses a second run for the same version', () => {
  const { repo, proposed } = fixture()
  assert.equal(run(repo, '0.2.0', proposed, '--skip-verify').status, 0)
  const again = run(repo, '0.2.0', proposed, '--skip-verify')
  assert.notEqual(again.status, 0)
  assert.match(again.stderr, /already exists/)
})

test('refuses when the tag or the remote branch exists', () => {
  const a = fixture()
  git(a.repo, 'tag', 'v0.2.0')
  assert.match(run(a.repo, '0.2.0', a.proposed, '--skip-verify').stderr, /tag v0\.2\.0 already exists/)
  const b = fixture()
  git(b.repo, 'push', '-q', 'origin', 'HEAD:refs/heads/chore/release-v0.2.0')
  assert.match(run(b.repo, '0.2.0', b.proposed, '--skip-verify').stderr, /already exists on origin/)
})

test('refuses when CHANGELOG.md changed on origin/main since the draft was made', () => {
  const { repo, proposed, base } = fixture()
  writeFileSync(join(repo, 'CHANGELOG.md'), '# Changelog\n\n## [Unreleased]\n\n- a thing\n- a new thing\n')
  git(repo, 'commit', '-qam', 'another entry')
  git(repo, 'push', '-q', 'origin', 'HEAD:main')
  const r = run(repo, '0.2.0', proposed, '--base', base, '--skip-verify')
  assert.notEqual(r.status, 0)
  assert.match(r.stderr, /CHANGELOG\.md changed on origin\/main since/)
})

test('refuses a bad version and a missing file; refuses a CHANGELOG identical to main', () => {
  const { repo, proposed } = fixture()
  assert.match(run(repo, 'v0.2', proposed).stderr, /is not X\.Y\.Z/)
  assert.match(run(repo, '0.2.0', '/nonexistent').stderr, /no such file/)
  writeFileSync(proposed, readFileSync(join(repo, 'CHANGELOG.md')))
  assert.match(run(repo, '0.2.0', proposed, '--skip-verify').stderr, /nothing to release/)
})

test('runs scripts/verify.sh versions in the worktree and stops when it fails', () => {
  const { repo, proposed } = fixture({ verify: 'echo "$@" > ../verify-args; exit 1' })
  const r = run(repo, '0.2.0', proposed)
  assert.notEqual(r.status, 0)
  assert.match(r.stderr, /scripts\/verify\.sh versions failed/)
  assert.equal(readFileSync(join(repo, '.claude/worktrees/verify-args'), 'utf8').trim(), 'versions')
  assert.equal(git(repo, 'ls-remote', '--heads', 'origin', 'chore/release-v0.2.0'), '', 'nothing is pushed')
})

test('stops and commits nothing when a file outside the four changes', () => {
  const { repo, proposed } = fixture({ verify: 'echo stray > stray.txt' })
  const r = run(repo, '0.2.0', proposed)
  assert.notEqual(r.status, 0)
  assert.match(r.stderr, /unexpected change: stray\.txt/)
  const wt = join(repo, '.claude/worktrees/release-v0.2.0')
  assert.equal(git(wt, 'rev-list', '--count', 'origin/main..HEAD'), '0', 'no commit was made')
})
