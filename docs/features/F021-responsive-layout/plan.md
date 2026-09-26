# F021 — Action Plan

**Depends on**: F001–F013, F017, F019, **F020** (Playwright tooling must be merged first).

Multiple PRs, each off `develop`, each shippable and each with its Playwright checks. Branch names: `feature/f021-<step>`. The CHANGELOG bullet (`**F021 — Responsive layout**`) is added in the first PR and edited as the rest land.

## PR 1 — Shell
- [x] Test first: `Layout` shows the permanent drawer on desktop and a hamburger-opened temporary drawer below `lg`; drawer closes on route change; padding `p: 2` on mobile and `p: 3` otherwise; content `maxWidth` about 1600.
- [x] Implement responsive `Layout`; group the settings items under a subheader in the nav.
- [x] `theme.ts`: `@media (pointer: coarse)` 44px minimum for `MuiIconButton`, `MuiButton`, `MuiListItemButton`.
- [x] Playwright: nav-mode checks on the landing route at all three viewports (`e2e/shell.spec.ts`); the no-overflow check passes on tablet and desktop, and on mobile stays a `test.fail` in `smoke.spec.ts` because the dashboard's "Upcoming recurring bills" widget is 522px wide (min-content) in a 358px column. Fixed in PR 4 (Dashboard batch), which must delete that marker.
- [x] CHANGELOG bullet.

## PR 2 — Shared primitives
- [x] `ResponsiveTable` (columns with roles and tablet priority, `renderCard` override; table on tablet/desktop, cards below `sm`; tables with 1–2 columns stay tables). Test first with a faked `matchMedia` (`src/test/viewport.ts`); breakpoint logic lives in `useBreakpointBand` (`src/hooks`). Column count for the 1–2-column rule excludes the actions column.
- [x] `ResponsiveDialog` (`fullScreen` below `sm`, one-column form grid helper). Test first.
- [x] `ResponsiveFilterBar` (inline bar vs. "Filters" button with active-count badge). Test first.
- [x] `PaginationControls` compact mode below `sm`. Test first.
- [x] Extract row-edit field components so inline editing and the mobile edit dialog share them (pattern documented for the migrations below).
  - PR 2 only documented the pattern (`frontend/CLAUDE.md`) and demonstrated it in `ResponsiveTable.test.tsx`; it is delivered by Transactions in PR 3 (`TransactionFormFields`). Each later page extracts its own.

## PR 3 — Pilot: Transactions
- [x] Migrate Transactions to all four primitives, with a `renderCard` override; Add and Edit open a `ResponsiveDialog` at every size (full screen below `sm`).
  - Decision (revised after review): Transactions never had inline row editing, only one combined add/edit form panel below the table. The panel is removed at every size: an `Add transaction` button in the page header and each row's/card's Edit open the fields (`TransactionFormFields`) in a `ResponsiveDialog` driven by the same form state and mutations (regular dialog from `sm` up, full screen below). A save error shows inside the open dialog (the page banner sits behind it). Pages that already have true inline row editing keep it on tablet/desktop (decide per page in PR 4).
  - Tablet hides only the Payment Method column (`tabletPriority: 'low'`); date, category, account, amount, description and actions stay. Cards (`renderCard`): description and signed, coloured amount on top, then `date · category`, then `account · payment method`, then Edit/Delete.
  - Filters: `ResponsiveFilterBar` replaces the outlined "Filters" panel (its "Clear filters" now shows only while a filter is active). The old panel box and heading are gone on all sizes.
  - `AccountTransactionList` (read-only embed in the account detail page) is not migrated: it has no actions and belongs to the Accounts batch of PR 4, where it can reuse the card layout.
- [x] Playwright spec (`e2e/transactions.spec.ts`): no overflow, cards on mobile, table on tablet/desktop, filter button on mobile, add dialog full-screen on mobile only, and no inline form panel at any size.
- [x] Adjust primitives if the pilot shows gaps (record the change here).
  - No primitive changed; the pilot fit the existing APIs. Note for tests: jsdom without `setViewportWidth` counts as the tablet band (neither `down(sm)` nor `up(lg)` matches), so assertions on the full desktop table need `setViewportWidth(VIEWPORT.desktop)`.

## PR 4 — Remaining features (batches)
Each batch: migrate pages, decide per-page details from the actual markup and record them under the batch, add Playwright checks, keep desktop behaviour unchanged.
- [ ] Accounts and transfers.
- [ ] Budgets and recurring templates.
- [ ] Investments (products, trades, snapshots, allocation).
- [ ] Settings pages (categories, investment categories, institutions, payment methods; keep 1–2 column tables as tables).
- [ ] Dashboard (fluid widget grid: 3, 2 and 1 columns), Export filters, Onboarding (outside `Layout`).

## PR 5 — Docs and ADR
- [ ] `docs/adr/0017-responsive-layout-strategy.md` and its row in `docs/adr/README.md`.
- [ ] `frontend/CLAUDE.md`: primitive-per-purpose guidance, no raw `useMediaQuery` in feature code, no hard-coded widths.
- [ ] Root `README.md` "Project status" entry; tick this plan; PR link on the CHANGELOG bullet once the PRs exist.

## Verification
- [ ] `npm run lint && npm test && npm run build && npm run e2e` green.
- [ ] Every feature page has a Playwright spec passing at 390, 768 and 1280.
- [ ] Manual, real browser at 360, 768 and 1280: no horizontal scroll on any page; nav, tables/cards, filters, dialogs and pagination behave per the spec; dark mode toggle reachable on all sizes.
- [ ] Desktop diff review: only nav and padding differ from before.
