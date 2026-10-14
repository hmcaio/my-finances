import i18n from '../i18n/i18n'
import { PROBLEM_TYPE_MESSAGES, VALIDATION_CODE_MESSAGES } from '../api/core/apiError'

/**
 * Test-only helpers resolving an RFC 9457 `type` / bean-validation `code` to its English text
 * (F033/ADR 0027 turned both maps' values into translation keys instead of literal English
 * strings) - every test asserting on a mapped error message that isn't itself testing locale
 * switching uses one of these instead of indexing `PROBLEM_TYPE_MESSAGES`/
 * `VALIDATION_CODE_MESSAGES` directly, which would now assert on the raw key string.
 *
 * Deliberately resolved against a fixed `en` `t()` (`i18n.getFixedT`), not the test's currently
 * active language: these call sites run at test-module collection time (inside a `describe`
 * block's body, e.g. `describeSettingsPage`'s `conflict.message`/`duplicateName.message`), before
 * any `it()`/`beforeEach` runs - reading the live, possibly-still-settling active language here
 * would race `src/test/setup.ts`'s `afterEach` reset against the next file's module evaluation
 * under `vite.config.ts`'s `isolate: false`. A test that specifically exercises a locale switch
 * (`apiError.test.ts`'s "F033 locale-aware messages") asserts on literal pt-BR text instead.
 */
export function resolveErrorMessage(type: string): string {
  return i18n.getFixedT('en')(PROBLEM_TYPE_MESSAGES[type] as never)
}

export function resolveValidationMessage(code: string): string {
  return i18n.getFixedT('en')(VALIDATION_CODE_MESSAGES[code] as never)
}
