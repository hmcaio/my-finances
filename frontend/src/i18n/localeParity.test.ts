import { describe, expect, it } from 'vitest'
import { resources } from './i18n'

/**
 * Build-time enforcement of full translation coverage (F033/ADR 0027): every namespace's
 * `pt-BR.json` must declare exactly the same keys as its `en.json`, nested objects included.
 * TypeScript's module augmentation (`i18next.d.ts`) only ever checks a `t()` call site against
 * the `en` resources, so a key missing from `pt-BR` (or a stray extra one) would otherwise only
 * surface at runtime, as a literal untranslated key string rendered on screen.
 */

/** All leaf-key paths in an object, e.g. `{ a: { b: 1 } }` -> `['a.b']`. */
function leafPaths(value: unknown, prefix = ''): string[] {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return [prefix]
  }
  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
    leafPaths(child, prefix ? `${prefix}.${key}` : key),
  )
}

describe('locale parity', () => {
  const namespaces = Object.keys(resources.en) as (keyof typeof resources.en)[]

  it('covers every namespace declared in resources.en', () => {
    expect(namespaces.length).toBeGreaterThan(0)
  })

  it.each(namespaces)('"%s" has identical keys in en and pt-BR', (namespace) => {
    const enKeys = leafPaths(resources.en[namespace]).sort()
    const ptBrKeys = leafPaths(resources['pt-BR'][namespace]).sort()

    expect(ptBrKeys).toEqual(enKeys)
  })
})
