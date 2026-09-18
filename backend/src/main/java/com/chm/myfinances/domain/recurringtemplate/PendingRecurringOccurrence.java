package com.chm.myfinances.domain.recurringtemplate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A not-yet-confirmed cycle of a {@link RecurringTemplate} (F007 spec). Not a persisted variant of
 * {@code Transaction} - it's a lightweight record that exists only until it's either confirmed
 * (converted into a real F004 {@code Transaction}, linked via {@code recurringTemplateVersionId})
 * or dismissed, at which point the row is deleted. Created once per cycle by the catch-up algorithm
 * ({@link RecurringOccurrenceGenerator}), never re-created for a cycle that already has one.
 *
 * <p>Entirely immutable - there's no "edit a pending occurrence" use case (F007 spec's confirm flow
 * accepts overrides on the resulting transaction, not on this record itself).
 */
public final class PendingRecurringOccurrence {

  private final UUID id;
  private final UUID templateId;
  private final UUID templateVersionId;
  private final LocalDate dueDate;

  private PendingRecurringOccurrence(
      UUID id, UUID templateId, UUID templateVersionId, LocalDate dueDate) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    this.templateVersionId =
        Objects.requireNonNull(templateVersionId, "templateVersionId must not be null");
    this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
  }

  /**
   * Creates a brand-new PendingRecurringOccurrence. {@code id} must come from the {@code
   * IdGenerator} port.
   */
  public static PendingRecurringOccurrence create(
      UUID id, UUID templateId, UUID templateVersionId, LocalDate dueDate) {
    return new PendingRecurringOccurrence(id, templateId, templateVersionId, dueDate);
  }

  /** Rebuilds a PendingRecurringOccurrence from already-validated persisted state. */
  public static PendingRecurringOccurrence reconstitute(
      UUID id, UUID templateId, UUID templateVersionId, LocalDate dueDate) {
    return new PendingRecurringOccurrence(id, templateId, templateVersionId, dueDate);
  }

  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public UUID getTemplateVersionId() {
    return templateVersionId;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }
}
