# 0034. Model goals and allocation as one aggregate, and project them deterministically from user-entered assumptions

Status: Accepted
Date: 2026-11-04

## Context
The app records what the user owns (investment products, holdings, manual snapshots, [ADR 0012](0012-investments-as-accounts-and-transfers.md), [0020](0020-investment-holdings-many-to-many.md)) but not what it is *for*, and it has nothing forward-looking: PRD §3 forbids price-derived investment math, and no existing feature estimates the future.

Two wishes arrived together: manage **goals** (retirement, a car, a PC upgrade) with estimates and projections, and **allocate investment products to purposes** (emergency fund, retirement, a car). They looked like two features, but a purpose without a target is only a tag, and a goal without linked products cannot be tracked. Two lists would have to be kept in sync. The FII `AllocationPlan` ([ADR 0023](0023-fii-allocation-plan-and-dividends.md)) is a different concept (a target mix *inside* the FII portfolio) and is untouched.

A projection also collides with PRD §3 ("no computed investment math") unless the line is drawn explicitly.

## Decision
- **One aggregate, `Goal`, with allocation as its link table.** `GoalAllocation(goalId, productId, percentage)` links a goal to an `InvestmentProduct`. A product can serve several goals; its percentages across all non-archived goals sum to at most 100, and the remainder is "unassigned". The link is at **product** level (the app already sums a product's holdings' latest snapshots), and only investment products can be linked (a savings balance is modeled as a Poupança or daily-liquidity product). Rejected: a separate "purpose" feature (two lists to keep in sync), a one-product-one-goal link (a single CDB often covers two purposes), fixed-amount splits (they drift as the value moves), and holding-level links (an account choice on every link for little gain; can be added later without breaking this model).
- **One uniform shape: a target amount and an optional target date.** No goal subtypes. Retirement and emergency-fund "helpers" in the UI only *suggest* a target number (months of expenses; desired income ÷ withdrawal rate) and are not stored. Rejected: typed goals with per-type rules, which would split the domain for a calculation the UI can do.
- **Projection is deterministic, computed on read, never stored, from user-entered assumptions only.** Inputs: the allocated value now (Σ percentage × the product's latest-snapshot value), a planned monthly contribution, and three **real** annual returns (pessimistic, base, optimistic). Everything is in **today's money**, so there is no inflation input. Monthly growth compounds at (1+r)^(1/12) − 1 with contributions at month end. Outputs per scenario: the value over time, the projected value and shortfall at the target date (or the months until the target is reached, capped at 50 years), and the monthly contribution required to hit the target on time.
- **This does not cross PRD §3.** The non-goal is about values *derived from prices* (quantity × market price, gain/loss). A projection derives nothing from prices: it compounds numbers the user typed, on top of a manually entered snapshot value, and is presented as an estimate. PRD §3 gets one sentence saying so.
- **Goals are edited in place, not versioned** (unlike budgets and recurring templates, [ADR 0002](0002-versioned-budget-and-recurring-template.md)): those are versioned because past months must keep their old meaning, while a projection only looks forward. The audit log ([ADR 0022](0022-audit-log-explicit-port-same-transaction.md)) records changes. Consequently the progress history applies the **current** split retroactively and is labeled as such; past splits are not stored.
- **A per-goal `showOnDashboard` flag (default on, cleared by archiving)** drives a dashboard card, with no limit on how many goals are selected. The dashboard reads one batch endpoint, not one projection call per goal.
- **Not in v1:** Monte Carlo (it needs a volatility guess the app has no data for), contribution step-up, account-level linking, actual-contribution tracking, stored allocation history. They go to PRD §9.

## Consequences
- The app gains its first forward-looking computation. It stays out of the price-derived math PRD §3 forbids, because only user-typed assumptions drive it.
- Projection accuracy is only as good as the assumptions and the freshness of the manual snapshots; the UI says "values in today's money" and shows the latest snapshot date.
- A product with goal allocations can no longer be hard-deleted (`409`); closing it is unaffected, and its value then reads as zero.
- Deleting or archiving a goal needs no cascade into investments: the link table is the only dependency, and `goal_allocations` rows go with the goal.
- Because splits are edited in place, the history chart is an "as of the current allocation" view; a future feature storing allocation history would supersede that label.
- The rate math needs a fractional power, which `BigDecimal` does not provide; money results are rounded to two decimals at the edges (verified, see F039's spec).
