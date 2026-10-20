# F029 — Action Plan

**Depends on**: F028 (built first), F019, F021.

## Frontend
- [ ] `src/api/investments/investmentSplits.ts`: `InvestmentSplit` type + `listInvestmentSplits`/`createInvestmentSplit`/`deleteInvestmentSplit`, against the regenerated `schema.ts` from F028.
- [ ] `src/api/investments/investmentSplitsQueries.ts`: key factory + `useInvestmentSplits`/`useCreateInvestmentSplit`/`useDeleteInvestmentSplit`. Test first (MSW: loads, create invalidates the list, delete invalidates the list).
- [ ] `src/mocks/handlers/investmentSplits.ts`: MSW fixtures, added to the shared handler list.
- [ ] `InvestmentProductDetailPage.tsx`: `SplitHistory` component (history table, delete action) + "Splits" panel + "Record split" dialog + delete-confirm dialog, wired exactly like the existing Snapshots panel (see spec.md's Decisions). Add `splitDialogOpen` to `anyDialogOpen`.
- [ ] `InvestmentProductDetailPage.test.tsx`: render the splits panel with fixture data; record a split (valid and the disabled-submit cases: empty unit field, equal before/after units); delete a split with confirm.

## Docs
- [ ] `docs/features/README.md` row for F029.
- [ ] Root `README.md` "Project status" entry.
- [ ] `CHANGELOG.md` `[Unreleased]` entry (`**F029 — Investment Splits (frontend)**`, user-visible: record/view/delete a product's split history from its detail page); PR link added once the PR is open.
- [ ] Tick this plan.

## Verification
- [ ] `npm run lint && npm test && npm run build` green.
- [ ] Manual, dev: open a product's detail page, record a split, see it appear in the history table immediately (no reload) and the "Value over time" table's `Units` column reflect the new ratio from the split's effective month onward.
- [ ] Manual: attempt a 1:1 ratio and an empty unit field — submit stays disabled.
- [ ] Manual: delete a split — history updates immediately, `Units` column reverts for points after the deleted split's date.
- [ ] Manual: a backend `400` (e.g. a future date typed directly, bypassing the date picker's `max`) shows a banner inside the dialog, same as every other form's error handling.
