# F033 — Internationalization (English / Brazilian Portuguese)

## Summary
Adds a UI language toggle (English / Brazilian Portuguese) covering every piece of app-owned text and all number/date/money formatting, via `react-i18next`. Frontend only: no backend, API or data-model change — the backend already sends no translatable text (ADR 0011, ADR 0026/F031, F032). Decision record: [ADR 0027](../../adr/0027-frontend-i18n-react-i18next.md).

Motivation: the app is built for a Brazilian user (BRL amounts, fuel prices in R$, `America/Sao_Paulo` test timezone) but every string and number is hardcoded English/`en-US`-shaped today. The hard requirement is completeness — a partially translated screen looks broken, not "in progress" — and that number/date formatting (not just strings) follows the chosen language.

## Scope
- `LocaleProvider`/`useLocale` (client-only, `localStorage`-persisted), mirroring the existing `ColorModeProvider` pattern.
- Translation resources for every UI string (labels, buttons, nav, validation/error messages) in `en` and `pt-BR`, with build-time parity enforcement.
- A shared, locale-aware number/date/money formatter replacing every ad hoc `.toFixed()` call and the two hardcoded `'en-US'` date formats.
- Translated display of seeded/built-in taxonomy row names (categories, payment methods, institutions, investment categories/sub-categories) by exact-string match against their migration-seeded names.
- Header toggle UI, tests, `frontend/CLAUDE.md`, ADR 0027, CHANGELOG.
- Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| `react-i18next` + `i18next`, two locales only (`en`, `pt-BR`). No `i18next-browser-languagedetector`. | Standard tooling for interpolation/pluralization; detection is simple enough to hand-write (one `navigator.language` check), matching the next decision's precedent instead of adding a third dependency. |
| `src/hooks/locale/{LocaleProvider.tsx,useLocale.ts}`, shaped exactly like `src/hooks/colorMode/{ColorModeProvider.tsx,useColorMode.ts}`: a context seeded once on mount (`localStorage.getItem('my-finances:locale')`, else `navigator.language.startsWith('pt') ? 'pt-BR' : 'en'`, defaulting to `'pt-BR'` when ambiguous), persisted back to `localStorage` on every change, calling `i18next.changeLanguage(locale)` as the side effect. `useLocale()` throws outside the provider, same as `useColorMode`. | Reuses a pattern already proven in this codebase for exactly this shape of per-device, backend-free preference — no new architecture to invent or review. |
| `App.tsx` wraps `<LocaleProvider>` alongside the existing `<ColorModeProvider>`; `Layout.tsx` gets a second header `IconButton` (next to the dark/light toggle) calling `useLocale().toggleLocale()`, swapping a flag/text icon and its `aria-label`. No settings page. | Matches the dark-mode toggle's placement and discoverability; a dedicated Settings page would be speculative (no other feature needs one yet, per root `CLAUDE.md`'s guidance against designing for hypothetical requirements). |
| Translation resources: `src/i18n/locales/{en,pt-BR}/<namespace>.json`, one namespace per `src/features/<area>` folder plus `common` (nav, shared buttons/labels) and `errors` (the two maps below). Statically imported (no HTTP backend plugin) — two locales' worth of JSON for this app's string count is trivial bundle weight. | Namespace boundaries mirror the existing feature-folder structure translators and reviewers already navigate by; static import avoids a runtime-loading dependency and failure mode the app doesn't need for 2 bundled languages. |
| TypeScript module augmentation (`src/i18n/i18next.d.ts`) types `t()` call sites against the `en` resources (the canonical key set). A dedicated test (`src/i18n/localeParity.test.ts`) asserts every namespace's `pt-BR.json` has exactly the same keys as its `en.json`. | TS catches an unknown/typo'd key at every call site; the parity test is still needed because TS only type-checks against the one declared-canonical resource, not cross-locale. Together they're the "full coverage enforced at build time" bar ADR 0027 sets. |
| `src/i18n/format.ts`: `formatMoney(amount, locale)`, `formatNumber(value, locale, fractionDigits)`, `formatDate(date, locale)` — thin wrappers over `Intl.NumberFormat`/`Intl.DateTimeFormat`, each preserving its call site's existing precision (2 for money, 3 for liters, 1 for odometer/km, per `docs/conventions/data-constraints.md`'s fuel-precision rule). Replaces every `.toFixed()` call across the ~28 files listed in [Structure](#structure), plus the hardcoded `'en-US'` in `groupByDay.ts`/`ActivityPage.tsx` (now driven by the active locale). | The point of real i18n is that a pt-BR screen reads `1.234,56` / `16 de março de 2026`, not a mix of translated labels and English-formatted numbers. Centralizing in one module means every call site changes once, not independently. |
| Amounts stay symbol-less in both locales — `formatMoney` never prepends `R$`/`$`. | PRD §3 already has no multi-currency support and no symbol display; introducing one is an unrelated decision this feature doesn't make. |
| Seed-name translation: `src/i18n/seedNames.ts` holds five `Record<string, TranslationKey>` lookups (categories, payment methods, institutions, investment categories, investment subcategories), keyed by the exact literal strings seeded in `V2`/`V12`/`V13`/`V14`/`V18`/`V20`'s migrations. A shared `translateSeedName(table, currentName, t)` helper is called wherever that table's rows render as a label (lists, selects, chips); a name not found in the lookup (new row, or a renamed built-in one) renders verbatim. | No backend/schema change — the existing `built_in` flag doesn't even cover most of the seeded rows (V14 only flags one row per category type). Exact-string matching degrades gracefully: the moment a user renames a seeded row, it's indistinguishable from their own data and correctly stops being translated. |
| `src/api/core/apiError.ts`'s `PROBLEM_TYPE_MESSAGES`/`VALIDATION_CODE_MESSAGES` become `type`/`code` → translation-key maps, resolved by calling the `i18next` singleton's `t(key)` directly inside `toApiError`/`summaryFor` (not the `useTranslation()` hook — this module has no React dependency today and shouldn't gain one). The resolved string is still baked into `ApiError.message` once, at catch time, exactly as today. | Keeps the existing non-React module shape and `unwrap()`/`defaultErrorMessage` contract (`docs/conventions/errors.md`) untouched; `i18next.t()` works identically outside a component. A banner already on screen does not retroactively re-translate if the user flips language afterward — acceptable, since error banners are dismissed well before that would matter. |
| MUI itself contributes no locale text in this app (confirmed: no MUI X `DataGrid`/`DatePicker` dependency, `MonthPicker` is a plain `<input type="month">`). No MUI `LocalizationProvider`/locale import is added. | Nothing to localize there; adding unused locale plumbing would be dead weight. `<input type="month">`'s own browser-chrome UI follows the OS locale, not this toggle — documented as an accepted, unfixable-from-JS limitation, not a bug. |
| Full coverage required at ship: every existing hardcoded string in scope gets both an `en` and `pt-BR` entry before this feature is considered done — no fallback-to-English-key runtime path to build or maintain. | Matches ADR 0027; a small app with no legacy i18n debt should not ship half-translated. |

## Structure
```
src/i18n/
  locales/
    en/<namespace>.json
    pt-BR/<namespace>.json
  i18n.ts              i18next.init(), static resource imports
  i18next.d.ts          TS module augmentation against the `en` resources
  format.ts             formatMoney / formatNumber / formatDate
  seedNames.ts           per-table seed-name -> translation-key lookups + translateSeedName()
  localeParity.test.ts
src/hooks/locale/
  LocaleProvider.tsx
  useLocale.ts
  useLocale.test.tsx
```

Files whose numeric/date display moves onto `src/i18n/format.ts` (every current `.toFixed()`/hardcoded-`'en-US'` call site): `features/transfers/TransferForm.tsx`, `features/transfers/TransfersPage.tsx`, `features/transfers/AccountTransferList.tsx`, `features/fii/AllocationPlanEditor.tsx`, `features/fii/FiiPage.tsx`, `features/fii/DividendHistorySection.tsx`, `features/fii/FiiNestedAllocationDonutChart.tsx`, `features/activity/groupByDay.ts`, `features/activity/ActivityPage.tsx`, `features/investmentProducts/InvestmentProductDetailPage.tsx`, `features/investmentProducts/InvestmentProductsSection.tsx`, `features/investmentProducts/ValueSeriesChart.tsx`, `features/budgets/BudgetsPage.tsx`, `features/budgets/BudgetVsActualReport.tsx`, `features/fuel/FuelPage.tsx`, `features/fuel/FuelTimeSeriesChart.tsx`, `features/recurringTemplates/RecurringTemplatesPage.tsx`, `features/recurringTemplates/PendingOccurrencesWidget.tsx`, `features/transactions/TransactionsPage.tsx`, `features/transactions/AccountTransactionList.tsx`, `features/investments/InvestmentsPage.tsx`, `features/investments/InvestmentAllocationChart.tsx`, `features/investments/FlatAllocationDonutChart.tsx`, `features/accounts/AccountsPage.tsx`, `features/accounts/AccountDetailPage.tsx`, `features/netWorth/NetWorthTrendChart.tsx`, `features/dashboard/AccountBalancesWidget.tsx`, `features/dashboard/SpendByCategoryWidget.tsx`, `components/feedback/BackupStatusBanner.tsx`.

## Testing
- `localeParity.test.ts`: every namespace's `pt-BR.json` key set equals its `en.json` key set.
- `useLocale.test.tsx` (mirrors `useColorMode.test.tsx`): initial detection from `localStorage` vs. `navigator.language` stub, persistence on toggle, throws outside the provider.
- `format.test.ts`: `formatMoney`/`formatNumber`/`formatDate` against both locales at each precision in use (2/3/1 decimals; date).
- One integration-level test (e.g. in `Layout.test.tsx` or a new `i18n.test.tsx`) that toggles the header control and asserts a known string switches language and a known amount switches separator style.
- **Existing page/component tests asserting literal formatted numbers must be reviewed**: `Intl.NumberFormat('en', ...)` inserts a thousands separator `.toFixed()` never did (`"1234.56"` → `"1,234.56"`), so any fixture using a 4-digit-or-larger amount needs its assertion updated even though the locale (`en`, jsdom's default `navigator.language`) doesn't change. Search test files for amount fixtures ≥ 1000 before relying on "default locale is already `en`, so most tests are unaffected."
- `npm run lint && npm test && npm run build` green.

## Docs
- ADR 0027 and its README row; `docs/features/README.md` row; PRD §3 (three new non-goals), §6.14, §9 (two new future directions).
- `frontend/CLAUDE.md`: new "Internationalization" section (locale provider shape, namespace/key convention, the formatter module, the seed-name lookup and its graceful-degradation-on-rename behavior, the apiError.ts `i18next.t()`-outside-React pattern, the `<input type="month">` browser-chrome limitation).
- Root `README.md`: "Project status" entry once built.
- `CHANGELOG.md` `[Unreleased]` entry (`**F033 — Internationalization**`, user-visible: English/Portuguese language toggle). No `Upgrade:` line (no compose, env or config change).

## Non-goals
- Any backend change, and any locale beyond `en`/`pt-BR` (ADR 0027, PRD §9).
- A currency symbol (`R$`/`$`) on amounts — unrelated to this feature (PRD §3).
- A settings page, or any backend-persisted preference — client-only, `localStorage`, same as dark mode.
- Translating user-entered data (category/account/institution names once renamed, transaction descriptions/notes, etc.).
- Retroactively re-translating an error banner already on screen after a language switch.
- Reacting to an OS-level language change while the app is already open (detected once, on load).
- MUI locale providers / MUI X (`DataGrid`, `DatePicker`) — not dependencies today, nothing to localize there.
- Fixing `<input type="month">`'s OS-locale-driven native picker chrome — not controllable from JS.

## Open questions
None.

## Dependencies
F001 (frontend skeleton). Reuses the `ColorModeProvider`/`useColorMode` pattern (F001 spec's "Dark mode toggle") as its structural precedent, without depending on its code. Touches `apiError.ts`'s message maps introduced by F031/F032, and every feature area's frontend display code, but changes no behavior any of those features specify.
