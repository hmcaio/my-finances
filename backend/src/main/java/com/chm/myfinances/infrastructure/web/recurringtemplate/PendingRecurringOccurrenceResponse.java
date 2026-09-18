package com.chm.myfinances.infrastructure.web.recurringtemplate;

import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of a {@link PendingRecurringOccurrence} (F007 spec's {@code GET .../pending},
 * dashboard-ready for F012). Includes {@code amount} (denormalized from the resolved {@code
 * RecurringTemplateVersion}) so the "upcoming recurring bills" widget can render without a second
 * round trip per row; {@code templateId} lets the frontend look up description/category/account
 * from its already-loaded template list (same {@code nameLookup} pattern F004/F006's frontend
 * screens use).
 */
public record PendingRecurringOccurrenceResponse(
    UUID id, UUID templateId, UUID templateVersionId, LocalDate dueDate, BigDecimal amount) {

  public static PendingRecurringOccurrenceResponse from(
      PendingRecurringOccurrence occurrence, RecurringTemplateVersion version) {
    return new PendingRecurringOccurrenceResponse(
        occurrence.getId(),
        occurrence.getTemplateId(),
        occurrence.getTemplateVersionId(),
        occurrence.getDueDate(),
        version.getAmount());
  }
}
