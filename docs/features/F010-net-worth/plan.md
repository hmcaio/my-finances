# F010 — Action Plan

**Depends on**: F003, F009, F015 (transitively F004, F005, F008).

## Backend
- [x] Write tests first for the point-in-time calculation: matches the PRD §5.9 formula exactly (asset accounts add, `INVESTMENT` accounts add, credit card accounts subtract) and returns the three parts.
- [x] Write tests for the as-of-aware account filter: an account closed after month M still counts in M, an account opened after M doesn't, an `INVESTMENT` account contributes `0` before its first snapshot.
- [x] Add `NetWorthQuery` application service implementing the above to make those tests pass, composing F003's `AccountBalanceQuery` (signed by `AccountType`; `INVESTMENT` balances come from F009's snapshots through it).
- [x] Write tests for the trend-series calculation (a value change is reflected on the correct date, both granularities; month points carry values forward through quiet months; the current month is evaluated at today), then implement it (change-date: distinct change dates → point calculation at each; month: point calculation at each month-end).
- [ ] REST controller: `GET /api/net-worth`, `GET /api/net-worth/trend` (with `granularity`).

## Frontend
- [ ] `src/api/netWorth.ts`.
- [ ] Net worth trend chart component with a change-date/monthly toggle, embedded in F012's dashboard.

## Verification
- [ ] Cross-check the point-in-time net worth value by hand against known account balances and investment snapshots for a test dataset.
- [ ] Confirm the trend chart's inflection points line up with actual transaction/transfer/snapshot dates.
- [ ] Close an account and confirm past months on the monthly chart are unchanged.
