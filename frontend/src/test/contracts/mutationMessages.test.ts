import { describe, expect, it } from 'vitest'

/**
 * F040: every mutation hook in `src/api/**\/*Queries.ts` either sets `meta.successMessage` (the
 * success toast, published by `MutationCache.onSuccess`) or is on the allowlist below with its
 * reason. A new write that ships without either fails here. The scan reads the hooks' source
 * rather than calling them: a hook is the body of an `export function use...` that calls
 * `useMutation(`.
 */
const SOURCES = import.meta.glob<string>('../../api/**/*Queries.ts', {
  query: '?raw',
  import: 'default',
  eager: true,
})

/** Mutation hooks that deliberately show no success toast, each with its reason. */
const NO_TOAST: Record<string, string> = {
  useLogin: 'signing in is not a write the user needs confirmed; the app opens on the page itself',
  useLogout: 'signing out ends in the login screen, which is the confirmation',
  useDownloadReports: 'a read with a side effect (it saves a file); the browser shows the download',
}

interface MutationHook {
  name: string
  file: string
  body: string
}

function mutationHooks(): MutationHook[] {
  const hooks: MutationHook[] = []
  for (const [file, source] of Object.entries(SOURCES)) {
    // Split before each top-level export so a hook's body ends where the next declaration starts.
    for (const block of source.split(/\n(?=export )/)) {
      const name = /^export function (use\w+)\(/.exec(block)?.[1]
      if (name && block.includes('useMutation(')) {
        hooks.push({ name, file: file.replace('../../api/', ''), body: block })
      }
    }
  }
  return hooks
}

describe('mutation success messages (F040)', () => {
  const hooks = mutationHooks()

  it('finds the mutation hooks (guards the scan itself)', () => {
    const names = hooks.map((hook) => hook.name)

    expect(names).toEqual(expect.arrayContaining(['useCreateTransaction', 'useLogin']))
    expect(hooks.length).toBeGreaterThan(40)
  })

  it('gives every mutation hook a success message or an allowlist entry', () => {
    const missing = hooks
      .filter((hook) => !hook.body.includes('successMessage:') && !(hook.name in NO_TOAST))
      .map((hook) => `${hook.name} (${hook.file})`)

    expect(missing, 'set meta.successMessage, or add the hook to NO_TOAST with a reason').toEqual(
      [],
    )
  })

  it('keeps the allowlist honest: every entry is a real mutation hook without a message', () => {
    const byName = new Map(hooks.map((hook) => [hook.name, hook]))

    for (const name of Object.keys(NO_TOAST)) {
      const hook = byName.get(name)
      expect(hook, `${name} is allowlisted but is not a mutation hook`).toBeDefined()
      expect(hook?.body, `${name} is allowlisted but sets a successMessage`).not.toContain(
        'successMessage:',
      )
    }
  })

  it('keeps successMessageOptions only on hooks that also set a message', () => {
    const orphans = hooks
      .filter(
        (hook) =>
          hook.body.includes('successMessageOptions') && !hook.body.includes('successMessage:'),
      )
      .map((hook) => hook.name)

    expect(orphans).toEqual([])
  })
})
