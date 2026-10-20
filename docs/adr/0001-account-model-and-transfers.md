# 0001. Model accounts with explicit types and transfers instead of a flat category-only ledger

Status: Accepted (amended by [0012](0012-investments-as-accounts-and-transfers.md): investments became an `INVESTMENT` account type, which changes the net worth formula below and the last consequence)
Date: 2026-09-12

## Context
The initial design tracked transactions by category only, with a single running cash total and credit card debt as a periodic manual balance snapshot (like an investment). Walking through a concrete case — logging a credit card grocery purchase — surfaced a real accounting flaw: the expense reduced the cash total immediately ("spent = paid"), but if the later credit card statement payment was also logged as an expense, the same spend was double-counted. If it wasn't logged, the credit card's manual balance snapshot drifted out of sync with what was actually charged.

Two fixes were considered: (a) keep the flat ledger and simply never log card payments as transactions, accepting that cash total assumes "spent = paid" and drop debt tracking entirely, or (b) reintroduce real accounts with balances, and a `Transfer` concept between them.

## Decision
Adopt (b). Each transaction belongs to a specific `Account` (`CHECKING` / `SAVINGS` / `CASH_WALLET` / `CREDIT_CARD`). An expense on an asset account reduces its balance; an expense on a credit card account *increases* the amount owed. Paying a credit card statement is a `Transfer` between two accounts — not a new expense — which decreases the source account and decreases the destination's amount owed (or increases its balance, if the destination is an asset account). Transfers are uncategorized, don't count toward budgets, and don't change net worth (an asset down and a liability down by the same amount nets to zero).

Net worth = Σ asset account balances − Σ credit card account balances + Σ latest investment snapshot per product.

## Consequences
- Credit card debt is now tracked accurately and automatically from real spend, not a disconnected manual number.
- The data model gained a `Transfer` entity and account-type-aware balance math, a meaningfully bigger build than a flat ledger (see PRD §5.4, §5.5, §5.9 and features F003/F004/F005/F010).
- Recurring templates (see [0002](0002-versioned-budget-and-recurring-template.md)) and every transaction now require an `account_id`.
- Accounts can never be hard-deleted once they have history (would orphan past net worth calculations) — only closed. The same rule was later extended to investment accounts/products (F008/F009) for consistency.
