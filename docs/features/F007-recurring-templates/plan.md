# F007 — Action Plan

**Depends on**: F001, F002, F003, F004, F015.

## Backend
- [x] Write tests first for `RecurringTemplate`/`RecurringTemplateVersion`'s domain rules: editing amount/day creates a new version rather than mutating one, `close()`/`active` toggling.
- [x] Add `domain/recurringtemplate/RecurringTemplate.java`, `RecurringTemplateVersion.java`, `PendingRecurringOccurrence.java`, implementing the above to make those tests pass.
- [x] Write tests first for the catch-up generation algorithm as a pure, unit-testable domain/application service (PRD §7.2 TDD emphasis — the highest-value place to apply it): given a template, its versions, `last_generated_for`, and "today", produce the list of cycles to generate. Cover normal monthly tick, multi-month catch-up after downtime, day-of-month clamping (29–31 on short months), version changes mid-catch-up (different past months use different versions), stopped template generates nothing, reactivated template resumes from current version — then implement the algorithm against those tests, before wiring persistence.
- [x] Add JPA entities (extend `AuditableEntity`), repositories, adapters for all three tables.
- [x] Flyway migration `V9__recurring_templates.sql` (see the migration-number-drift note below), plus the deferred FK on `transactions.recurring_template_version_id`.
- [x] Wire catch-up generation to run on startup and/or lazily before serving recurring-related requests.
- [x] Write tests for the confirm flow (an override at confirmation time doesn't create a new template version), then implement application services: create, edit (new version), stop, reactivate, list pending, confirm (creates a `Transaction` via F004's service, with override support), dismiss pending.
- [x] Subscribe to F003's account-closed event/port to auto-deactivate matching templates.
- [x] REST controllers + DTOs.

## Frontend
- [ ] `src/api/recurringTemplates.ts`.
- [ ] `src/features/recurringTemplates` — settings list, create/edit-cap forms, stop/reactivate.
- [ ] Pending-occurrences widget (confirm with pre-filled/overridable transaction form, dismiss).

## Verification
- [ ] Create a template, confirm a pending occurrence generates on/after its day-of-month.
- [ ] Simulate downtime (manipulate `last_generated_for` backward, or stop/restart across a month boundary) and confirm multiple dated pending occurrences appear, not zero and not merged.
- [ ] Confirm an occurrence with an overridden amount; verify the resulting transaction has the override, not the template's amount, and no new version was created.
- [ ] Stop a template; confirm no further occurrences generate. Close its account; confirm it auto-stops.
