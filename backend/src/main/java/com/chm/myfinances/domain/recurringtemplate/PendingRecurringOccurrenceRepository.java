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

  void deleteById(UUID id);

  /**
   * Deletes every pending occurrence for {@code templateId} - called when a template is deactivated
   * (manual stop, or F003's account-closed port), per F007 spec: "deleted once confirmed ... or the
   * template is deactivated".
   */
  void deleteByTemplateId(UUID templateId);

  /**
   * Whether a pending occurrence already exists for this exact {@code (templateId, dueDate)} pair -
   * defense in depth against the catch-up job ever double-generating the same cycle (F007 spec: "if
   * one doesn't already exist for that cycle").
   */
  boolean existsByTemplateIdAndDueDate(UUID templateId, LocalDate dueDate);
}
