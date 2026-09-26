# F021 — Responsive Layout (desktop, tablet, mobile)

## Summary
Makes the frontend usable on phones and tablets as well as desktop. Today the shell is a permanent 240px drawer, `main` has fixed `p: 3`, 16 files render `<Table>`, and nothing is breakpoint-aware. This feature adds a responsive shell, a small set of shared responsive primitives, and migrates every page to them. Frontend only: no backend, API or data-model change. Verified with the Playwright tooling from [F020](../F020-playwright-e2e/spec.md).

## Scope
- Responsive `Layout` (nav modes, padding, max width) and theme touch-target overrides.
- Shared primitives: `ResponsiveTable`, `ResponsiveDialog`, `ResponsiveFilterBar`, compact `PaginationControls`.
- Migration of every feature page to those primitives.
- Playwright geometry checks per page (F020 helpers), unit tests with a faked viewport (`matchMedia`).
- ADR, `frontend/CLAUDE.md`, CHANGELOG.
- Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| Breakpoints are the MUI defaults (xs 0, sm 600, md 900, lg 1200). Mobile is below 600, tablet is 600–1199, desktop is 1200 and up. Supported from 360px wide. | No custom breakpoints to maintain; `theme.breakpoints` is what every MUI `sx` already speaks. |
| Nav: permanent drawer on desktop (unchanged); tablet and mobile use one temporary drawer behind an AppBar hamburger that closes on route change. Settings items are grouped under a subheader in all modes. | One extra mode. A bottom nav fits about 5 items and there are 12, so it would need an overflow menu. |
| Content padding: `p: 2` on mobile, `p: 3` otherwise; `maxWidth` about 1600px, left-aligned. | Reclaims width on phones; keeps tables from stretching on ultrawide screens. |
| Tables with 3 or more columns render as cards below `sm`; tablet keeps the table and hides low-priority columns behind a per-row expander (chevron) that shows them as label/value pairs; tables with 1–2 columns stay tables at all sizes. | Cards are the best mobile reading experience for data-heavy lists; small settings tables gain nothing from a second rendering. |
| One shared `ResponsiveTable` in `components/table`: columns declared once with a role per column (`primary`, `secondary`, `hideOnCard`) and a priority for tablet hiding; an optional `renderCard` override for screens the generic card can't serve (Transactions). | Avoids 10+ near-duplicate card implementations. |
| Editing: desktop and tablet keep inline row editing (`InlineEditActions`) unchanged. On mobile, Edit opens a full-screen dialog reusing the row's field components. | Editing inputs in place on a card is cramped; a dialog needs the row-edit fields extracted so both surfaces share them. |
| `ResponsiveDialog` wraps MUI `Dialog` and sets `fullScreen` below `sm`; form grids collapse to one column at `xs`. | One wrapper instead of `useMediaQuery` in every dialog. |
| `ResponsiveFilterBar`: below `sm` filters collapse behind a "Filters" button opening a sheet or full-screen dialog, with an active-filter count badge; tablet and desktop keep the inline wrapping bar. | Several `TextField`s in a row don't fit a phone; hiding them by default frees the screen while the badge keeps active filters visible. |
| `PaginationControls` renders compact below `sm` (prev/next plus "Page X of Y", page-size selector hidden). | Single shared change. |
| The Add button stays in the page header and opens the same `ResponsiveDialog`; no floating action button. | Least new UI; a FAB would overlap card lists and pagination. |
| Dashboard widgets use a fluid grid (3, 2 and 1 columns by band); every other non-table page stacks to a single column below `sm`. Per-page details are decided during each implementation batch and recorded in the plan. | The dashboard has no chart library (`AccountBalancesWidget`, `SpendByCategoryWidget`); designing each page up front, before touching its markup, would be guesswork. |
| Touch targets: under `@media (pointer: coarse)` the theme sets a 44px minimum on `MuiIconButton`, `MuiButton` and `MuiListItemButton`. Desktop density is unchanged. | Real touch devices, not narrow desktop windows, are what need bigger targets. |
| Components use the shared primitives or `theme.breakpoints` in `sx`; raw `useMediaQuery` in feature code is discouraged (it lives inside the primitives). | Keeps breakpoint logic in one place; documented in the ADR and `frontend/CLAUDE.md`. |
| Verification: Playwright geometry/DOM checks per page at 390, 768 and 1280 (no horizontal overflow, correct nav mode, correct table/card mode) plus Vitest unit tests for the primitives with a faked viewport (`matchMedia`). No screenshot baselines. | Layout can't be checked in jsdom; geometry checks are stable across OSes. |

## Structure

```
src/components/layout/Layout.tsx              responsive shell (drawer modes, padding, max width)
src/components/table/ResponsiveTable.tsx      table on tablet/desktop, cards on mobile
src/components/table/PaginationControls.tsx   compact variant below sm
src/hooks/useBreakpointBand.ts                the one breakpoint hook the primitives share (mobile/tablet/desktop)
src/test/viewport.ts                          fake matchMedia from a viewport width, for unit tests
src/components/feedback/ResponsiveDialog.tsx  full-screen below sm
src/components/layout/ResponsiveFilterBar.tsx inline bar vs. Filters button
src/theme.ts                                  coarse-pointer touch-target overrides
e2e/<area>.spec.ts                            per-page geometry checks (F020 helpers)
```

## Testing
- Test first for each primitive (Vitest, `matchMedia` faked from a viewport width via `src/test/viewport.ts`): `ResponsiveTable` renders table vs. cards, hides tablet-low-priority columns, honours `renderCard`; `ResponsiveDialog` full-screen flag; `ResponsiveFilterBar` collapse and badge count; `PaginationControls` compact mode; `Layout` drawer modes and close-on-navigate.
- Playwright: every feature page at 390/768/1280 passes no-overflow, nav-mode and table/card-mode checks.
- All existing Vitest tests keep passing; desktop behaviour is unchanged apart from nav and padding.
- `npm run lint && npm test && npm run build && npm run e2e` green.

## Docs
- ADR 0017 (responsive strategy: MUI breakpoints, cards vs. tables, shared primitives, geometry assertions instead of screenshots) and its row in `docs/adr/README.md`.
- `frontend/CLAUDE.md`: which primitive to use for what, no raw `useMediaQuery` in feature code, no hard-coded widths.
- `docs/features/README.md` row; root `README.md` "Project status" entry once built.
- `CHANGELOG.md` `[Unreleased]`: one bullet, `**F021 — Responsive layout** — <summary>`, added in the first PR and edited as later PRs land. No `Upgrade:` line.

## Non-goals
- PWA, installability and offline support.
- Native gestures such as swipe-to-delete.
- Landscape-specific layouts (the same breakpoints apply).
- Cross-browser e2e (Chromium only, per F020).
- Any backend change.

## Definition of done
- At 360, 768 and 1280px wide no page overflows horizontally, and every feature page passes the F020 geometry checks.
- All existing Vitest tests pass; new primitives have unit tests with a faked viewport (`matchMedia`).
- Desktop behaviour is unchanged apart from the nav and padding tweaks.

## Open questions
None.
