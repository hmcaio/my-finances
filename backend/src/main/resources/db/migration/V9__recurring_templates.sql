-- F007: Recurring Templates.
--
-- Creates `recurring_templates`, `recurring_template_versions` (the versioned amount/day-of-month
-- history, same shape as F006's `budgets`/`budget_versions`), and `pending_recurring_occurrences`
-- (a lightweight, short-lived row deleted once its occurrence is confirmed or dismissed) tables
-- (PRD S5.7, F007 spec). Also adds the FK from `transactions.recurring_template_version_id` to
-- `recurring_template_versions`, deferred there as a nullable, unconstrained column by F004's
-- V5__transactions.sql since this table didn't exist yet.
--
-- Numbered V9, not V7: F007's spec/plan originally said `V7__recurring_templates.sql`, but F005
-- (transfers) already claimed `V7` and F006 (budgets) claimed `V8` by the time this feature was
-- built - same renumbering story as F003 (V3->V4), F004 (V4->V5), F005 (V5->V7), and F006 (V6->V8)'s
-- own notes in their spec.md files.
--
-- recurring_templates.last_generated_for is a nullable `date`, always stored as the first day of
-- the month (same YearMonth<->date convention as budget_versions.effective_from) - null until the
-- catch-up job (RecurringOccurrenceGenerator) generates a template's first pending occurrence.
--
-- recurring_template_versions.day_of_month has a plain CHECK (1-31); which months clamp a given
-- day to their own shorter length is generation-time domain logic (RecurringTemplateVersion.
-- dueDateFor), not something a column constraint can express. UNIQUE (template_id, effective_from)
-- backs the "only one version per (template, month)" invariant as defense in depth alongside the
-- application layer's own same-month replace check (F006 budget_versions precedent).
--
-- pending_recurring_occurrences has no UNIQUE (template_id, due_date) constraint: the catch-up
-- algorithm's own existsByTemplateIdAndDueDate check is the only guard against double-generating a
-- cycle (F007 spec explicitly calls this a defense-in-depth check, not an invariant the schema
-- itself must enforce), since a legitimate confirmation-then-reactivation flow could in principle
-- reuse a due_date across different template_version_id rows over the template's lifetime.
--
-- amount uses numeric(19,2), matching the currency-minor-unit precision convention established by
-- V4/V5/V7/V8's own amount/monthly_cap columns.

CREATE TABLE recurring_templates (
    id                  uuid PRIMARY KEY,
    category_id         uuid NOT NULL REFERENCES categories(id),
    account_id          uuid NOT NULL REFERENCES accounts(id),
    description         varchar(150) NOT NULL,
    active              boolean NOT NULL DEFAULT true,
    last_generated_for  date,
    created_at          timestamptz NOT NULL,
    last_modified_at    timestamptz NOT NULL
);

CREATE INDEX idx_recurring_templates_account_id ON recurring_templates(account_id);

CREATE TABLE recurring_template_versions (
    id                 uuid PRIMARY KEY,
    template_id        uuid NOT NULL REFERENCES recurring_templates(id),
    amount             numeric(19,2) NOT NULL,
    day_of_month       int NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    effective_from     date NOT NULL,
    created_at         timestamptz NOT NULL,
    last_modified_at   timestamptz NOT NULL,
    CONSTRAINT uq_recurring_template_versions_template_id_effective_from UNIQUE (template_id, effective_from)
);

CREATE INDEX idx_recurring_template_versions_template_id ON recurring_template_versions(template_id);

CREATE TABLE pending_recurring_occurrences (
    id                    uuid PRIMARY KEY,
    template_id           uuid NOT NULL REFERENCES recurring_templates(id),
    template_version_id   uuid NOT NULL REFERENCES recurring_template_versions(id),
    due_date              date NOT NULL,
    created_at            timestamptz NOT NULL,
    last_modified_at      timestamptz NOT NULL
);

CREATE INDEX idx_pending_recurring_occurrences_template_id ON pending_recurring_occurrences(template_id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_recurring_template_version_id
    FOREIGN KEY (recurring_template_version_id) REFERENCES recurring_template_versions(id);
