package com.chm.myfinances.domain.recurringtemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link PendingRecurringOccurrence} (ADR 0004). Implemented by an adapter in
 * {@code infrastructure/persistence/recurringtemplate}.
 */
public interface PendingRecurringOccurrenceRepository {

  PendingRecurringOccurrence save(PendingRecurringOccurrence occurrence);

  Optional<PendingRecurringOccurrence> findById(UUID id);

  /** Every pending occurrence, dashboard-ready (F007 spec's {@code GET .../pending}). */
  List<PendingRecurringOccurrence> findAll();

  /**
   * Every pending occurrence for {@code templateId} - used by {@code RecurringTemplateService}'s
   * cap-edit path to re-resolve which {@code RecurringTemplateVersion} each still-pending
   * occurrence should point to after a new version is created (a same-month correction updates the
   * existing version in place, so it doesn't need this - only a forward-only new-version edit can
   * leave an already-generated occurrence pointing at a now-superseded version).
   */
  List<PendingRecurringOccurrence> findByTemplateId(UUID templateId);

  void deleteById(UUID id);

  /**
   * Deletes every pending occurrence for {@code templateId} - called when a template is deactivated
   * (manual stop, or F003's account-closed port), per F007 spec: "deleted once confirmed ... or the
   * template is deactivated".
   */
  void deleteByTemplateId(UUID templateId);

  /**
   * Whether a pending occurrence already exists for this exact {@code (templateId, dueDate)} pair.
   * A read only: it can't be the guard against double-generating a cycle, because two callers can
   * both see "absent" before either inserts - {@link #insertIfAbsent} is the atomic version.
   */
  boolean existsByTemplateIdAndDueDate(UUID templateId, LocalDate dueDate);

  /**
   * Inserts {@code occurrence} unless a pending occurrence for the same {@code (templateId,
   * dueDate)} already exists, atomically - the only safe way for the catch-up job to generate a
   * cycle when two runs can overlap (the startup runner and a page load, two tabs, ...). Backed by
   * a {@code UNIQUE (template_id, due_date)} constraint, so a lost race is a no-op rather than a
   * duplicate row.
   *
   * @return {@code true} if this call inserted the row, {@code false} if the cycle already had one
   */
  boolean insertIfAbsent(PendingRecurringOccurrence occurrence);
}
