# F010 — Net Worth

## Summary
The net worth formula and its trend series over time (PRD §5.9). A read-only computed feature — no new persisted entity, purely an aggregation over F003/F004/F005/F009's data.

## Scope
- `net_worth(as_of_date) = Σ asset account balances − Σ credit card account balances + Σ latest snapshot per investment product`.
- Trend series: net worth evaluated at each date where any underlying value changed (transaction, transfer, or investment snapshot).
- Out of scope: any storage of computed net worth — always derived at read time (PRD §5.9: "computed, not stored").

## Backend

### Domain / Application
- No new domain entity. `NetWorthQuery` (application-layer service) composes:
  - F003's `AccountBalanceQuery` for every non-closed `Account`, summed with sign by `AccountType` (`CHECKING`/`SAVINGS`/`CASH_WALLET` added, `CREDIT_CARD` subtracted) — matches PRD §5.9 exactly.
  - F009's `LatestInvestmentSnapshotQuery`, summed across all products.
- `as_of_date` point calculation: run the above as of a single date.
- Trend series calculation: determine the distinct set of "change dates" (every transaction/transfer date, every snapshot date) up to the requested range, then evaluate the point calculation at each — a straightforward but potentially expensive query at scale; given this is a single-user local app with a manageable data volume (PRD §7.3), no incremental/cached materialization is needed for v1. If performance becomes an issue later, a cached/materialized trend table is a candidate future optimization, not a v1 requirement.

### API
- `GET /api/net-worth?asOf=` — single point value.
- `GET /api/net-worth/trend?from=&to=` — `{ date, netWorth }[]` for the chart.

## Frontend
- Net worth trend line chart — reusable component embedded in F012's dashboard.
- No standalone screen beyond the dashboard widget is required by the PRD, though a dedicated "net worth" page with date-range controls is a reasonable minor addition if useful; not specified further here since the PRD doesn't call for one beyond the dashboard.

## Dependencies
F003 (account balances), F009 (investment snapshots). Transitively depends on F004/F005 for balances to be meaningful.
