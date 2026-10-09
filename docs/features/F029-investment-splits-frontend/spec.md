# F029 — Investment Splits (frontend)

## Summary
Adds a "Splits" panel to `InvestmentProductDetailPage` so a stock/FII split or reverse split ([F028](../F028-investment-splits-backend/spec.md), [ADR 0025](../../adr/0025-investment-split-as-read-side-adjustment.md)) can be recorded and reviewed. Frontend only — no new backend behavior; this consumes F028's endpoint and renders its already-corrected `cotasHeld`/monthly `units` numbers, which need no frontend change themselves.

## Scope
- `src/api/investments/investmentSplits.ts` (HTTP functions, following every other `src/api/<area>.ts` module's shape) + `investmentSplitsQueries.ts` (key factory, `useInvestmentSplits`, `useCreateInvestmentSplit`, `useDeleteInvestmentSplit` — ADR 0016/F019 pattern: global invalidation on a successful mutation, no optimistic updates).
- `src/mocks/handlers/investmentSplits.ts` (MSW fixtures for both pages and tests).
- `InvestmentProductDetailPage.tsx`: a new product-level "Splits" panel (history table + "Record split" action), placed with the product-level "Holdings" panel rather than inside the per-holding sections (Current value/Snapshots), since a split is scoped to the product, not to one holding.
- Out of scope: any change to the "Value over time" table's existing `Units` column or the "Trades" table — both already render whatever the backend (F028) returns; no frontend math.

## Decisions
- **Table shape mirrors `SnapshotHistory`**: a plain `Table` (not `ResponsiveTable`) — few enough columns (Effective date, Ratio, Notes, Actions) to stay readable at every width per F021's 1–2–3 column rule, same reasoning `SnapshotHistory`/`HoldingsTable` already use. No inline edit (F028/ADR 0025: no edit, delete-only), so each row only gets a delete `IconButton`, no `InlineEditActions`.
- **Ratio input**: two adjacent integer `TextField`s ("Before units" / "After units", e.g. `1`/`10`), not a single decimal-factor field — mirrors the backend's `beforeUnits`/`afterUnits` shape (ADR 0025) and lets the user type the ratio exactly as the broker states it. Displayed in the history table as `"{before} : {after}"`.
- **Add dialog**: `ResponsiveDialog` + `FormGrid`, same pattern as "Record snapshot" — date `TextField` (`type="date"`, defaults to `today()`, `slotProps.htmlInput.max = today()` to keep the future-date rejection visible client-side before the round trip), the two unit `TextField`s (`type="number"`, `min: 1`, integer step), an optional notes `TextField` (`multiline`, `slotProps.htmlInput.maxLength = 500`). Submit disabled while either unit field is empty/non-positive or both are equal (client-side mirror of the backend's `400`s, same "disable the submit button" style every other dialog in this page already uses).
- **Delete**: `ConfirmDialog`, same pattern as snapshot/holding delete — no special-casing, since F028 gives a split no downstream dependents.
- **Error handling**: `defaultErrorMessage(err)` with no `conflictMessage` override — F028 defines no `409` case for this entity.

## Frontend
- `src/api/investments/investmentSplits.ts`: `InvestmentSplit` interface (`id`, `investmentProductId`, `effectiveDate`, `beforeUnits`, `afterUnits`, `additionalNotes`), `listInvestmentSplits(productId)`, `createInvestmentSplit(payload)`, `deleteInvestmentSplit(id)`.
- `src/api/investments/investmentSplitsQueries.ts`: `investmentSplitKeys.list(productId)`; `useInvestmentSplits(productId)`; `useCreateInvestmentSplit()`/`useDeleteInvestmentSplit()` mutations (global invalidation, no `skipInvalidate`).
- `src/mocks/handlers/investmentSplits.ts`: MSW handlers for the three endpoints, registered in the shared handlers list.
- `InvestmentProductDetailPage.tsx`:
  - New `SplitHistory` component (same shape as `SnapshotHistory`): columns Effective date, Ratio (`"{before} : {after}"`), Notes, Actions (delete only).
  - New "Splits" `Paper` panel, placed directly after the "Holdings" panel (both product-level) and before the `selectedHolding &&` block (both of those are holding-level): header row with "Splits" title + "Record split" `Button`, same layout as the "Holdings"/"Snapshots" panel headers.
  - New dialog state (`splitDialogOpen`, `splitForm`, `recordingSplit`) and delete-confirm state (`deleteSplitTarget`, `deletingSplit`), following the exact `snapshotForm`/`snapshotDialogOpen`/`deleteTarget` pattern already in this file.
  - `anyDialogOpen` gains `splitDialogOpen`.
- Tests: `InvestmentProductDetailPage.test.tsx` gains cases for the splits panel (render history, record a split, validation-disabled submit, delete with confirm) — same style as the existing snapshot test cases in that file.

## Dependencies
F028 (backend endpoint and the corrected totals this panel's sibling "Value over time"/"Trades" sections already render), F019 (TanStack Query conventions), F021 (responsive table/dialog primitives).
