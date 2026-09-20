# F010 — Net Worth

## Summary
The net worth formula and its trend series over time (PRD §5.9). A read-only computed feature — no new persisted entity, purely an aggregation over F003/F004/F005/F009's data. Updated by [ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md): investments are `INVESTMENT` accounts, so the formula is one sum over accounts by type, and the series can be monthly.

## Scope
- `net_worth(as_of_date) = Σ asset account balances + Σ investment account balances − Σ credit card account balances`, where asset = `CHECKING`/`SAVINGS`/`CASH_WALLET`, investment = `INVESTMENT` (its balance is the sum of its products' latest snapshots, F009), and every balance is as of the date.
- Which accounts count at a date: opened on or before it and either never closed or closed after it. An `INVESTMENT` account has no opening date and contributes `0` until its first snapshot.
- Trend series in two granularities: at each date where any underlying value changed (transaction, transfer, or investment snapshot), or at each month-end.
- Out of scope: any storage of computed net worth — always derived at read time (PRD §5.9: "computed, not stored").

## Decisions
- **The closed filter is as-of-aware.** An earlier draft summed "every non-closed account", which drops an account from every past month once it is closed and would corrupt any historical series. A closed account still counts for every date before its `closed_date` (PRD §5.4: "still counted correctly in past net worth"), and an account counts only from its `opening_balance_date`.
- **Monthly points are evaluated at month-end** (the current month at today) and carry the last value forward through months with no activity, so a chart has a point per month rather than gaps.
- **The parts are returned separately** (assets, liabilities, investments) so gross and net totals are both chartable without a second call.

## Backend

### Domain / Application
- No new domain entity. `NetWorthQuery` (application-layer service) composes F003's `AccountBalanceQuery` over the accounts that count at the date (rule above), summed with sign by `AccountType` (`CHECKING`/`SAVINGS`/`CASH_WALLET`/`INVESTMENT` added, `CREDIT_CARD` subtracted). `INVESTMENT` balances already come from F009's `LatestInvestmentSnapshotQuery` through `AccountBalanceQuery`, so there is no separate investment term.
- `as_of_date` point calculation: run the above as of a single date, returning the total and its three parts.
- Trend series calculation: for change-date granularity, determine the distinct set of "change dates" (every transaction/transfer date, every snapshot date) up to the requested range, then evaluate the point calculation at each. For month granularity, evaluate at each month-end in the range. A straightforward but potentially expensive query at scale; given this is a single-user local app with a manageable data volume (PRD §7.3), no incremental/cached materialization is needed for v1. If performance becomes an issue later, a cached/materialized trend table is a candidate future optimization, not a v1 requirement.

### API
- `GET /api/net-worth?asOf=` — single point value: `{ date, netWorth, assets, liabilities, investments }`.
- `GET /api/net-worth/trend?from=&to=&granularity=CHANGE_DATE|MONTH` — `{ date, netWorth, assets, liabilities, investments }[]` for the chart. `granularity` defaults to `CHANGE_DATE` (today's behaviour); with `MONTH` there is one point per calendar month overlapping the range, each dated month-end (the current month, today).

## Frontend
- Net worth trend line chart with a change-date/monthly toggle — reusable component embedded in F012's dashboard.
- No standalone screen beyond the dashboard widget is required by the PRD, though a dedicated "net worth" page with date-range controls is a reasonable minor addition if useful; not specified further here since the PRD doesn't call for one beyond the dashboard.

## Dependencies
F003 (account balances), F009 (investment snapshots, `INVESTMENT` balances). Transitively depends on F004/F005 for balances to be meaningful.
