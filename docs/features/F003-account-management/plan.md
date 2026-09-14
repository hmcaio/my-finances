# F003 — Action Plan

**Depends on**: F001, F015.

## Backend
- [ ] Write tests first for `Account`'s domain rules: opening balance/date immutable after creation, `close()` sets `closedDate` and the account then rejects further activity.
- [ ] Add `domain/account/Account.java`, `AccountType` enum, implementing the above to make those tests pass.
- [ ] Add `AccountJpaEntity` (extends `AuditableEntity`), repository, adapter.
- [ ] Flyway migration `V3__accounts.sql`.
- [ ] Write tests for the list application service (closed accounts excluded by default), then implement: create, edit (name/institution only), close, get, list (with closed-account filter).
- [ ] Write tests for `AccountBalanceQuery` (opening-balance-only case for now), then implement it: computes running balance from opening balance (+ transactions/transfers once F004/F005 exist — revisit tests and implementation when those land).
- [ ] Domain event or port (`AccountClosedNotifier` or similar) fired on close, for F007 to consume later without F003 depending on F007.
- [ ] REST controller + DTOs; no delete endpoint.

## Frontend
- [ ] `src/api/accounts.ts`.
- [ ] `src/features/accounts` — list (with closed toggle), create/edit form, detail view, close action with confirmation.

## Verification
- [ ] Create an account, confirm opening balance/date can't be edited afterward.
- [ ] Close an account, confirm it disappears from the default list/picker but is still viewable with history-to-date preserved.
