# F012 — Action Plan

**Depends on**: F003, F004, F006, F007, F009, F010, F015.

## Backend
- [ ] (Optional but recommended) `GET /api/dashboard` composing the underlying feature endpoints into one response.
- [ ] Monthly-spend-by-category query (current month, per category), if not already covered by an existing F004/F006 endpoint.

## Frontend
- [ ] `src/features/dashboard` page, grid layout.
- [ ] Monthly spend by category widget.
- [ ] Embed F006's budget-vs-actual component.
- [ ] Account balances overview widget (from F003's account list).
- [ ] Embed F010's net worth trend chart.
- [ ] Embed F009's allocation chart.
- [ ] Embed F007's pending-occurrences widget with working confirm/dismiss.

## Verification
- [ ] With a populated test dataset, confirm every widget renders correct data matching its owning feature's own views.
- [ ] Confirm confirming/dismissing a pending recurring occurrence from the dashboard updates the widget without a full page reload (or with one, if that's the simpler v1 approach — either is acceptable).
