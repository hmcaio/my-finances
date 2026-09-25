# 0017. Allow hard-deleting an account that has no history

Status: Accepted
Date: 2026-09-25

## Context
The PRD said accounts are never hard-deleted, only closed (§5.8's delete-safety note, F003 spec: "no `DELETE` endpoint"). The rule protects history: past balances and net worth (§5.9) are computed from an account's rows, so deleting an account that has any would rewrite the past. But it also means an account created by mistake (wrong type, duplicate, typo) stays in the list, in every export, and pins its institution against deletion (§5.10) for good, with closing as the only remedy. Products already have the right precedent: hard delete is allowed exactly while the product has zero history (§5.8, F008).

## Decision
- **Reverses the "never hard-deleted" rule for accounts with no history.** `DELETE /api/accounts/{id}` deletes an account, open or closed, only when nothing references it; otherwise it answers `409` and the user closes the account instead. `404` for an unknown id, `204` on success.
- **History** is any row that references the account: `transactions.account_id`, `transfers.from_account_id` / `to_account_id`, `recurring_templates.account_id` (active or not) or `investment_products.account_id` (open or closed). These are exactly the tables whose foreign keys point at `accounts`, so the check matches what the database would reject, and the FKs stay as the backstop against a concurrent insert. No migration.
- **Hexagonal shape**: `domain/account/AccountUsageChecker` is a port owned by the account aggregate; `infrastructure/account/RealAccountUsageChecker` implements it over the four repositories' new `existsByAccountId` methods, so `AccountService` does not depend on other aggregates (same shape as `HasInvestmentHistoryChecker`, ADR 0004). Closing an account still keeps everything; deleting is for the mistake case only.
- **Accepted caveat: an account with only an opening balance is not "history" for this rule, but it does count in past net worth** (§5.9 counts an account from its `opening_balance_date`). Deleting it therefore changes past net worth figures. We accept that: treating any opening balance as history would make the feature useless for the very mistake it exists for, since every non-investment account has one. The confirmation dialog states it.
- Deleting the last account returns the app to onboarding (F011), because the gate derives from "zero accounts, closed ones included". Expected, and the dialog mentions it.

## Consequences
- The PRD (§5.4, §5.8, §6.2) and the F003 spec are updated; F008's and F017's notes that "accounts are never deleted" no longer hold as stated. An institution is still pinned by any account that exists, but a history-free account can now be deleted to free it.
- Deletion is permanent: an account deleted with only an opening balance disappears from past net worth and from later exports. Anyone who wants to keep the past intact closes instead.
- Every new table that references `accounts` must be added to `AccountUsageChecker`, or a delete would surface as a `500` from the FK instead of the intended `409`. `RealAccountUsageCheckerTest` (one case per referencing table) is the place to extend.
