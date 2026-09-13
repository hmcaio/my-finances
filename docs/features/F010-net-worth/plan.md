# F010 — Action Plan

**Depends on**: F003, F009 (transitively F004, F005).

## Backend
- [ ] Write tests first for the point-in-time calculation: matches the PRD §5.9 formula exactly (asset accounts add, credit card accounts subtract, investments add).
- [ ] Add `NetWorthQuery` application service implementing the above to make those tests pass, composing F003's account balances (signed by `AccountType`) and F009's latest investment snapshots.
- [ ] Write tests for the trend-series calculation (a value change is reflected on the correct date), then implement it (distinct change dates → point calculation at each).
- [ ] REST controller: `GET /api/net-worth`, `GET /api/net-worth/trend`.

## Frontend
- [ ] `src/api/netWorth.ts`.
- [ ] Net worth trend chart component, embedded in F012's dashboard.

## Verification
- [ ] Cross-check the point-in-time net worth value by hand against known account balances and investment snapshots for a test dataset.
- [ ] Confirm the trend chart's inflection points line up with actual transaction/transfer/snapshot dates.
