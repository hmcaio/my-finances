# Versioned history and recurring generation (backend)

Read when touching `Budget`, `RecurringTemplate`, `AllocationPlan` (or their versions) or the recurring-occurrence generator. Why versioned and why lazy: ADR 0002, ADR 0003.

- **Versioned history** (`Budget`+`BudgetVersion`, `RecurringTemplate`+`RecurringTemplateVersion`, `AllocationPlan`+`AllocationPlanVersion`): edits create a new forward-only version; the effective one for month X is the latest `effectiveFrom <= X` (`resolveEffective`, pure domain logic). Editing a month that already has a version replaces it in place instead of duplicating: the service decides which, the version class only performs the replace.
- **A `BudgetVersion` with a `null` cap is a tombstone** ("budget stopped from this month", issue #61, `V16`): any `getMonthlyCap()` reader branches on `isTombstone()`; the budgets report omits such a budget for that month. Data-layer detail: [data-constraints.md](data-constraints.md).
- **Recurring generation is lazy/catch-up** (the app isn't always running): `RecurringOccurrenceGenerator` is a pure function; `RecurringOccurrenceCatchUpService` is the only place it touches persistence, run at startup and before the pending list is served.
  - A pending occurrence stores only a version id and its amount is resolved live, so creating a new version must realign existing pending occurrences (`setCap` does).
  - `runCatchUp` is deliberately *not* `@Transactional`: one transaction would let a broken template roll back the others.
  - It is safe to run concurrently (startup runner vs a page load, two tabs, React `StrictMode`'s double fetch in dev): a cycle is generated with `PendingRecurringOccurrenceRepository.insertIfAbsent` (`INSERT ... ON CONFLICT DO NOTHING`, backed by `UNIQUE (template_id, due_date)`, V11). `existsByTemplateIdAndDueDate` + `save` is the racy shape to avoid: two callers both see "absent" and both insert (issue #20). A native insert bypasses JPA auditing, so it sets the audit timestamps itself.
  - Its concurrency test: [backend-testing.md](backend-testing.md).
