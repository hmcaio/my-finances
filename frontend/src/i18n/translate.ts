import type { CustomTypeOptions, ParseKeys } from 'i18next'

type Namespace = keyof CustomTypeOptions['resources']

/**
 * A namespace-qualified translation key that exists in the canonical `en` resources
 * (`'accounts:accountTypes.checking'`). Key tables for fixed enums, seeded names and audit fields
 * are typed with it, so a typo'd or removed key is a compile error even though the helpers that
 * read them take a loosely typed `t`.
 */
export type QualifiedKey = { [N in Namespace]: `${N}:${ParseKeys<N>}` }[Namespace]

/**
 * The `t` of any `useTranslation(...)`, whatever namespaces it was created for. A `TFunction` is
 * branded by its namespaces, so one scoped to `['ledger', 'common']` does not fit one scoped to
 * `'accounts'`; a `never` key parameter is (contravariantly) assignable from every one of them, so
 * callers pass their own `t` with no cast. Use it only with `translateKey`, which takes a
 * `QualifiedKey` and so keeps the key checked.
 */
export type Translate = (key: never, options?: never) => string

/**
 * Translates a qualified key with any `t` (optionally with interpolation values): the one place
 * the `never` key and options parameters are cast.
 */
export function translateKey(
  t: Translate,
  key: QualifiedKey,
  options?: Record<string, unknown>,
): string {
  return options === undefined ? t(key as never) : t(key as never, options as never)
}
