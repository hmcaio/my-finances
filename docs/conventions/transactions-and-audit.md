# Transaction boundaries and the audit log (backend)

The headline rule (a use case with more than one write is `@Transactional`, opted in explicitly; audited writes included) is in `backend/CLAUDE.md`. Single repository calls are self-transactional. Without the annotation, a failure between two writes leaves a half-created aggregate (a template with no version) or a duplicate (a confirmed occurrence that wasn't deleted).

- A buy/sell with a `resultingBalance` is such a use case: `TransferService.createTradeConfirmation` is `@Transactional` (transfer + per-line snapshot/holding-close effects, `TransferServiceTransactionalTest`). It is its own top-level method, not an overload the plain `create` delegates into (F027 replaced the old 9-argument `create`/6-argument-plain-delegate pair with two independent methods; see "Splitting a method" in `CODING_STANDARDS.md` for the invariant that split silently dropped).
- This includes cascades across aggregates: `AccountService.close()` is `@Transactional` because it triggers template deactivation through a port, and `stop()`'s own `@Transactional` just joins it.

## Audit log (F025, ADR 0022)

Any write use case instrumented with `AuditRecorder` is, by definition, at least two writes (the business change, then the audit insert), so **every such use case needs `@Transactional`**, even one that was a single write (and therefore self-transactional) before. Every new write use case calls the `AuditLog` port via `AuditRecorder` in the same transaction as its change; `ArchitectureTest.writeUseCasesDependOnAuditLog` fails an application `@Service` with a `@Transactional` write method that doesn't depend on it.

- **Overloads**: where several overloads of the same method name exist and a caller outside the class might hit any of them (e.g. `TransactionService.create`'s four overloads: the controller calls the 10-argument one, `RecurringTemplateService.confirmPending` calls the 8-argument one), every externally-reachable overload needs its own `@Transactional`, not just the one with the actual write logic. Self-invocation between overloads bypasses the Spring proxy, so only the exact method an external caller's own (proxied) call lands on gets a transaction boundary.
- **Origin**: a cascade that needs a different `origin` depending on who triggered it (a user's own click vs. another aggregate's cascade, e.g. `RecurringTemplateService.stop`) takes an explicit `AuditOrigin` parameter on an internal overload, with the public no-origin method defaulting to `USER`. Both overloads carry `@Transactional` for the same reason.
- The `audit_log` table holds amounts, descriptions, notes and entity names by design; it must never be echoed into a log line ([logging.md](logging.md)).

## Testing a boundary

Transaction-boundary tests (`*ServiceTransactionalTest`) must not be `@Transactional` themselves: see [backend-testing.md](backend-testing.md).
