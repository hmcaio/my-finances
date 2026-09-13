# F010 — Action Plan

**Depends on**: F003, F009 (transitively F004, F005).

## Backend
- [ ] Add `NetWorthQuery` application service: point-in-time calculation composing F003's account balances (signed by `AccountType`) and F009's latest investment snapshots.
- [ ] Add trend-series calculation (distinct change dates → point calculation at each).
- [ ] REST controller: `GET /api/net-worth`, `GET /api/net-worth/trend`.
- [ ] Tests: point calculation matches the PRD §5.9 formula exactly (asset accounts add, credit card accounts subtract, investments add), trend series reflects a value change on the correct date.

## Frontend
- [ ] `src/api/netWorth.ts`.
- [ ] Net worth trend chart component, embedded in F012's dashboard.

## Verification
- [ ] Cross-check the point-in-time net worth value by hand against known account balances and investment snapshots for a test dataset.
- [ ] Confirm the trend chart's inflection points line up with actual transaction/transfer/snapshot dates.
