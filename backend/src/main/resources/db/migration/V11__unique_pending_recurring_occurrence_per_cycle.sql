-- F007 follow-up (issue #20): at most one pending occurrence per recurring-template cycle.
--
-- V9 deliberately left `pending_recurring_occurrences` without a UNIQUE (template_id, due_date)
-- and relied on the catch-up job's own `existsByTemplateIdAndDueDate` check to avoid generating a
-- cycle twice. That check is a read followed by a write, so two catch-up runs that overlap (the
-- startup runner and a page load, two browser tabs, React StrictMode's double mount in dev) both
-- see "absent" and both insert - the same bill then appears twice and can be confirmed into two
-- transactions. The constraint below makes the database the guard;
-- `PendingRecurringOccurrenceRepository.insertIfAbsent` (`INSERT ... ON CONFLICT DO NOTHING`)
-- turns a lost race into a no-op.
--
-- V9's other reason for leaving it out - a due date reused across different template_version_id
-- rows over a template's lifetime - doesn't hold: a pending row is deleted once it is confirmed,
-- dismissed or its template is stopped, so uniqueness only ever applies among the rows that are
-- pending right now, and a later reuse of the same due date happens after the earlier row is gone.
--
-- Existing duplicates (from the race above) are collapsed first, keeping the earliest-created row
-- of each (template_id, due_date) pair (id breaks a created_at tie), or the constraint could not
-- be added. Both copies of a duplicate reference the same version - the cycle resolves to one - so
-- nothing is lost by keeping either.

DELETE FROM pending_recurring_occurrences duplicate
USING pending_recurring_occurrences keep
WHERE duplicate.template_id = keep.template_id
  AND duplicate.due_date = keep.due_date
  AND (duplicate.created_at, duplicate.id) > (keep.created_at, keep.id);

ALTER TABLE pending_recurring_occurrences
    ADD CONSTRAINT uq_pending_recurring_occurrences_template_id_due_date
    UNIQUE (template_id, due_date);
