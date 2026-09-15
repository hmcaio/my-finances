# F003 — Action Plan

**Depends on**: F001, F015.

## Backend
- [x] Write tests first for `Account`'s domain rules: opening balance/date immutable after creation, `close()` sets `closedDate` and the account then rejects further activity.
- [x] Add `domain/account/Account.java`, `AccountType` enum, implementing the above to make those tests pass.
- [x] Add `AccountJpaEntity` (extends `AuditableEntity`), repository, adapter.
- [x] Flyway migration `V4__accounts.sql` (V3 was already taken by F002's `V3__bound_name_column_lengths.sql`, added after F002 shipped - see spec.md).
- [x] Write tests for the list application service (closed accounts excluded by default), then implement: create, edit (name/institution only), close, get, list (with closed-account filter).
- [x] Write tests for `AccountBalanceQuery` (opening-balance-only case for now), then implement it: computes running balance from opening balance (+ transactions/transfers once F004/F005 exist — revisit tests and implementation when those land).
- [x] Domain event or port (`AccountClosedNotifier` or similar) fired on close, for F007 to consume later without F003 depending on F007.
- [x] REST controller + DTOs; no delete endpoint.

## Frontend
- [ ] `src/api/accounts.ts`.
- [ ] `src/features/accounts` — list (with closed toggle), create/edit form, detail view, close action with confirmation.

## Verification
- [ ] Create an account, confirm opening balance/date can't be edited afterward.
- [ ] Close an account, confirm it disappears from the default list/picker but is still viewable with history-to-date preserved.
