# F007 — Action Plan

**Depends on**: F001, F002, F003, F004.

## Backend
- [ ] Add `domain/recurringtemplate/RecurringTemplate.java`, `RecurringTemplateVersion.java`, `PendingRecurringOccurrence.java`.
- [ ] Write the catch-up generation algorithm as a pure, unit-testable domain/application service first (PRD §7.2 TDD emphasis — this is the highest-value place to apply it): given a template, its versions, `last_generated_for`, and "today", produce the list of cycles to generate. Cover with tests before wiring persistence: normal monthly tick, multi-month catch-up after downtime, day-of-month clamping (29–31 on short months), version changes mid-catch-up (different past months use different versions).
- [ ] Add JPA entities (extend `AuditableEntity`), repositories, adapters for all three tables.
- [ ] Flyway migration `V7__recurring_templates.sql`, plus the deferred FK on `transactions.recurring_template_version_id`.
- [ ] Wire catch-up generation to run on startup and/or lazily before serving recurring-related requests.
- [ ] Application services: create, edit (new version), stop, reactivate, list pending, confirm (creates a `Transaction` via F004's service, with override support), dismiss pending.
- [ ] Subscribe to F003's account-closed event/port to auto-deactivate matching templates.
- [ ] REST controllers + DTOs.
- [ ] Tests: full catch-up scenarios end to end, confirm-with-override doesn't create a new version, stopped template generates nothing, reactivated template resumes from current version.

## Frontend
- [ ] `src/api/recurringTemplates.ts`.
- [ ] `src/features/recurringTemplates` — settings list, create/edit-cap forms, stop/reactivate.
- [ ] Pending-occurrences widget (confirm with pre-filled/overridable transaction form, dismiss).

## Verification
- [ ] Create a template, confirm a pending occurrence generates on/after its day-of-month.
- [ ] Simulate downtime (manipulate `last_generated_for` backward, or stop/restart across a month boundary) and confirm multiple dated pending occurrences appear, not zero and not merged.
- [ ] Confirm an occurrence with an overridden amount; verify the resulting transaction has the override, not the template's amount, and no new version was created.
- [ ] Stop a template; confirm no further occurrences generate. Close its account; confirm it auto-stops.
