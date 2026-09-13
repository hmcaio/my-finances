# F003 — Action Plan

**Depends on**: F001.

## Backend
- [ ] Add `domain/account/Account.java`, `AccountType` enum, with immutable opening balance/date and a `close()` behavior method.
- [ ] Add `AccountJpaEntity` (extends `AuditableEntity`), repository, adapter.
- [ ] Flyway migration `V3__accounts.sql`.
- [ ] Application services: create, edit (name/institution only), close, get, list (with closed-account filter).
- [ ] `AccountBalanceQuery`: computes running balance from opening balance (+ transactions/transfers once F004/F005 exist — stub to opening-balance-only for now, revisit when those land).
- [ ] Domain event or port (`AccountClosedNotifier` or similar) fired on close, for F007 to consume later without F003 depending on F007.
- [ ] REST controller + DTOs; no delete endpoint.
- [ ] Tests: opening balance/date immutability, closed account rejects new activity, closed accounts excluded from default list.

## Frontend
- [ ] `src/api/accounts.ts`.
- [ ] `src/features/accounts` — list (with closed toggle), create/edit form, detail view, close action with confirmation.

## Verification
- [ ] Create an account, confirm opening balance/date can't be edited afterward.
- [ ] Close an account, confirm it disappears from the default list/picker but is still viewable with history-to-date preserved.
