package com.chm.myfinances.infrastructure.web.recurringtemplate;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/recurring-templates/pending/{id}/confirm} (F007 spec, PRD
 * S5.7/S6.5): {@code amount}/{@code date}/{@code accountId}/{@code description}/{@code
 * additionalNotes} are optional overrides on the resulting transaction (falling back to the
 * occurrence's own defaults when omitted) and never create a new {@code RecurringTemplateVersion}.
 *
 * <p>{@code paymentMethodId} is the one mandatory field here, not an override: {@code
 * RecurringTemplate} (F007 spec's field list) has no payment-method of its own to default to, but
 * {@code Transaction.paymentMethodId} is mandatory (F004) - so confirming always requires picking
 * one, same as creating any other transaction.
 */
public record ConfirmPendingOccurrenceRequest(
    BigDecimal amount,
    LocalDate date,
    UUID accountId,
    @NotNull UUID paymentMethodId,
    @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes) {

  // amount, when given, must still be positive - @Positive can't be combined with "optional" via
  // a single annotation on a nullable field, so this is intentionally left to the domain's own
  // Transaction.requireValidAmount() check (same defense-in-depth precedent as every other
  // aggregate's constructor invariant, ADR 0004).
}
